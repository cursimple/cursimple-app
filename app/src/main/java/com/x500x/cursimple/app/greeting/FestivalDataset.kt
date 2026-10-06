package com.x500x.cursimple.app.greeting

import android.content.Context
import com.x500x.cursimple.app.download.DownloadPurpose
import com.x500x.cursimple.app.download.DownloadRequest
import com.x500x.cursimple.app.download.MirrorDownloadResult
import com.x500x.cursimple.app.download.MirrorDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.time.LocalDate

/**
 * Download the generated festival dataset independently of releases. Return null before valid
 * cached coverage exists so [FestivalCalendar] can calculate a fallback.
 */
internal object FestivalDataset {
    private const val REPOSITORY = "cursimple/cursimple-app"
    private const val REF = "main"
    private const val PATH = "data/calendar/cn-festivals.json"
    private const val FILE_NAME = "cn-festivals.json"
    private const val SUPPORTED_VERSION = 1

    internal data class Parsed(
        val firstYear: Int,
        val lastYear: Int,
        val days: Map<String, List<String>>,
    ) {
        fun covers(date: LocalDate): Boolean = date.year in firstYear..lastYear
    }

    @Volatile
    private var memory: Parsed? = null

    private fun file(context: Context) = File(File(context.applicationContext.filesDir, "calendar"), FILE_NAME)

    internal fun parse(text: String): Parsed? = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: return@runCatching null
        if (version > SUPPORTED_VERSION) return@runCatching null
        val firstYear = root["firstYear"]?.jsonPrimitive?.intOrNull ?: return@runCatching null
        val lastYear = root["lastYear"]?.jsonPrimitive?.intOrNull ?: return@runCatching null
        val days = root["days"]?.jsonObject?.mapValues { (_, ids) ->
            (ids as? JsonArray)?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
        }.orEmpty()
        if (days.isEmpty() || firstYear > lastYear) null else Parsed(firstYear, lastYear, days)
    }.getOrNull()

    /** Null means no dataset coverage; an empty list means no festival on [date]. */
    fun idsOn(context: Context, date: LocalDate): List<String>? {
        val table = memory ?: load(context)?.also { memory = it } ?: return null
        if (!table.covers(date)) return null
        return table.days[date.toString()].orEmpty()
    }

    private fun load(context: Context): Parsed? = runCatching {
        file(context).takeIf { it.isFile }?.readText()?.let(::parse)
    }.getOrNull()

    suspend fun sync(context: Context, downloader: MirrorDownloader): Boolean {
        val request = DownloadRequest(
            purpose = DownloadPurpose.GithubRaw,
            url = "https://raw.githubusercontent.com/$REPOSITORY/$REF/$PATH",
            repository = REPOSITORY,
            ref = REF,
            path = PATH,
        )
        val result = downloader.downloadText(request, accept = "application/json")
        val text = (result as? MirrorDownloadResult.Success)?.value ?: return false
        val parsed = parse(text) ?: return false
        return withContext(Dispatchers.IO) {
            runCatching {
                val target = file(context)
                target.parentFile?.mkdirs()
                val temp = File(target.parentFile, "$FILE_NAME.tmp")
                temp.writeText(text)
                if (!temp.renameTo(target)) {
                    target.delete()
                    temp.renameTo(target)
                }
                memory = parsed
                true
            }.getOrDefault(false)
        }
    }
}

/** Dataset IDs use snake_case enum names. */
internal fun datasetIdOf(name: String): String =
    name.replace(Regex("(?<=[a-z])(?=[A-Z])"), "_").lowercase()

private val festivalById: Map<String, Festival> by lazy { Festival.entries.associateBy { datasetIdOf(it.name) } }
private val termById: Map<String, SolarTerm> by lazy { SolarTerm.entries.associateBy { datasetIdOf(it.name) } }

internal fun greetingResForId(id: String): GreetingRes? =
    festivalById[id]?.greetingRes() ?: termById[id]?.greetingRes()

internal fun isKnownDatasetId(id: String): Boolean = id in festivalById || id in termById
