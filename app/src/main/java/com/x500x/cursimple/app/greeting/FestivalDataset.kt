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
 * 节日、节气的日期表：仓库里 `data/calendar/cn-festivals.json` 那一份，由
 * `scripts/gen_cn_calendar.py` 按寿星天文历生成，一次列好往后几十年每一天。
 *
 * 日期不写死在代码里：打开 App 联网时静默下一次，存在本地，查的时候按日期直接取。
 * 以后要加节日、修正日期，改数据文件就行，不用等发版。
 * 本地还一次都没下成功时返回 null，由 [FestivalCalendar] 在本机现算兜底。
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

    /** 解析数据文件；版本不认识或者内容是空的都当作不可用，不拿坏数据顶掉好的。 */
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

    /**
     * [date] 这天的节日、节气 id；本地还没有数据表或者表里没覆盖这一年时返回 null，
     * 调用方自己兜底。这一天什么都不是时返回空列表。
     */
    fun idsOn(context: Context, date: LocalDate): List<String>? {
        val table = memory ?: load(context)?.also { memory = it } ?: return null
        if (!table.covers(date)) return null
        return table.days[date.toString()].orEmpty()
    }

    private fun load(context: Context): Parsed? = runCatching {
        file(context).takeIf { it.isFile }?.readText()?.let(::parse)
    }.getOrNull()

    /** 联网取一份最新的，校验通过才换掉本地那份。 */
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
                // 先写临时文件再改名：写到一半被杀掉也不会留下半个文件
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

/** 数据表里的 id 是枚举名的蛇形写法：`NationalDay` → `national_day`。 */
internal fun datasetIdOf(name: String): String =
    name.replace(Regex("(?<=[a-z])(?=[A-Z])"), "_").lowercase()

private val festivalById: Map<String, Festival> by lazy { Festival.entries.associateBy { datasetIdOf(it.name) } }
private val termById: Map<String, SolarTerm> by lazy { SolarTerm.entries.associateBy { datasetIdOf(it.name) } }

/** 数据表里的一个 id 对应哪条问候；这一版还不认识的新节日返回 null，跳过就好。 */
internal fun greetingResForId(id: String): GreetingRes? =
    festivalById[id]?.greetingRes() ?: termById[id]?.greetingRes()

internal fun isKnownDatasetId(id: String): Boolean = id in festivalById || id in termById
