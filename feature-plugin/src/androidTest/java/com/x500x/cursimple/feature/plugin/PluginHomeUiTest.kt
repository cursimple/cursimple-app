package com.x500x.cursimple.feature.plugin

import android.content.res.Configuration
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.core.plugin.install.*
import com.x500x.cursimple.core.plugin.manifest.*
import com.x500x.cursimple.core.plugin.packageformat.PluginPackageLayout
import com.x500x.cursimple.core.plugin.storage.PluginFileStore
import com.x500x.cursimple.feature.plugin.home.*
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PluginHomeUiTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var record: InstalledPluginRecord
    private lateinit var cache: PluginHomeCache
    private val calls = AtomicInteger()
    private var fail = false
    private var failWith: Throwable? = null
    private var loggedOut = false
    private val visible = mutableStateOf(true)
    private val alpha = PluginHomeAccount("account-a", "示例同学甲", "2099000001", "示例大学")
    private val beta = PluginHomeAccount("account-b", "示例同学乙", "2099000002", "示例大学")
    private fun section(course: String) = PluginHomeSection(
        id = "grades", columns = listOf("课程名称", "成绩", "学分", "绩点"),
        rows = listOf(listOf(course, "88", "3", "3.8")), savedAt = 1_790_000_000_000,
        summary = listOf(PluginHomeField("课程门数", "1")),
    )

    @Before fun prepare() {
        val manifest = PluginManifest(
            "home-ui-qa", "示例学校教务插件", version = "1", versionCode = 1, entry = "main.js",
            permissions = listOf(PluginPermission.NetworkFetch, PluginPermission.StoragePlugin),
            allowedHosts = listOf("school.example.edu"),
            home = PluginHomeSpec("ui/home.js", "https://school.example.edu/home", "https://school.example.edu/login",
                listOf(
                    PluginHomeSectionSpec("grades", "成绩查询", mapOf("en" to "Grades", "zh-TW" to "成績查詢")),
                    PluginHomeSectionSpec("plan", "培养计划", mapOf("en" to "Curriculum", "zh-TW" to "培養計畫")),
                    PluginHomeSectionSpec("credits", "学分进度", mapOf("en" to "Credits", "zh-TW" to "學分進度")),
                    PluginHomeSectionSpec("overview", "学籍信息", mapOf("en" to "Student details", "zh-TW" to "學籍資訊")),
                )),
        )
        val files = PluginPackageLayout(mapOf(
            "manifest.json" to homeJson.encodeToString(manifest).toByteArray(),
            "main.js" to "export async function run() {}".toByteArray(),
            "ui/home.js" to "export async function sync() {}".toByteArray(),
        ))
        val directory = PluginFileStore(context).writeLayout(manifest, files, PluginInstallSource.Local)
        record = InstalledPluginRecord(manifest.id, manifest.name, version = "1", versionCode = 1,
            storagePath = directory.path, installedAt = "qa", source = PluginInstallSource.Local)
        cache = PluginHomeCache(context, record.installKey)
        cache.clear()
        cache.update {
            it.withSection(alpha, section("数据库原理")).withSection(beta, section("计算机网络"))
                .copy(activeAccountId = alpha.id)
        }
    }

    @After fun clean() {
        if (::cache.isInitialized) cache.clear()
        if (::record.isInitialized) WebLoginCredentialStore(context).clear(record.pluginId)
        if (::record.isInitialized) File(record.storagePath).deleteRecursively()
    }

    private fun show(width: Int = 411, scale: Float = 1f, dark: Boolean = false, language: String = "zh-CN",
        systemLanguage: String = language, openGrades: Boolean = true) {
        val configuration = Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        val systemConfiguration = Configuration(configuration).apply { setLocale(Locale.forLanguageTag(systemLanguage)) }
        val localized = context.createConfigurationContext(configuration)
        val density = context.resources.displayMetrics.widthPixels / width.toFloat()
        compose.setContent {
            CompositionLocalProvider(
                LocalContext provides localized, LocalConfiguration provides systemConfiguration,
                LocalDensity provides Density(density, scale),
            ) {
                MaterialTheme(colorScheme = if (dark) darkColorScheme(
                    primary = Color(0xffaecbfa), primaryContainer = Color(0xff243c59),
                    onPrimaryContainer = Color(0xffd6e6ff), surface = Color(0xff111820),
                    background = Color(0xff111820), surfaceVariant = Color(0xff25313d),
                ) else lightColorScheme(
                    primary = Color(0xff287b62), primaryContainer = Color(0xffd6eee4),
                    onPrimaryContainer = Color(0xff123c2e), surface = Color(0xfff4f8f5),
                    background = Color(0xfff4f8f5), surfaceVariant = Color(0xffdfe9e2),
                )) {
                    if (visible.value) PluginHomeScreen(record, onBack = {}, queryRunner = { _, _, sectionId, _ ->
                        calls.incrementAndGet()
                        failWith?.let { throw it }
                        if (fail) throw java.io.IOException("offline")
                        if (loggedOut) buildJsonObject { put("loggedIn", false) } else buildJsonObject {
                            put("loggedIn", true)
                            put("account", homeJson.encodeToJsonElement(alpha))
                            if (sectionId != null) put("section", homeJson.encodeToJsonElement(section("算法设计")))
                        }
                    })
                }
            }
        }
        waitForRows(openGrades)
    }

    private fun waitForRows(openGrades: Boolean = true) {
        compose.waitUntil(15_000) {
            compose.onAllNodesWithTag("plugin-home-credential-status").fetchSemanticsNodes().isNotEmpty()
        }
        if (openGrades) openSection("grades")
    }

    /** Opens a menu entry as its own page and scrolls to the first saved row. */
    private fun openSection(id: String) {
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-tab-$id"))
        compose.onNodeWithTag("plugin-home-tab-$id").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-row-0"))
    }

    private fun backToMenu() {
        compose.onNodeWithContentDescription("返回插件列表").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("plugin-home-credential-status").fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenBitmap(): android.graphics.Bitmap {
        compose.waitForIdle()
        repeat(5) {
            instrumentation.uiAutomation.takeScreenshot()?.let { return it }
            android.os.SystemClock.sleep(200)
        }
        error("The emulator did not return a screenshot")
    }

    private fun capture(name: String) {
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-row-0"))
        captureScreen(name)
    }

    private fun captureScreen(name: String) {
        val bitmap = screenBitmap()
        File(context.filesDir, "plugin-home-qa/$name.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun savedResultsOpenOfflineAndOnlyManualRefreshQueries() {
        show()
        assertEquals(0, calls.get())
        compose.onNodeWithText("数据库原理").assertExists()
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-query"))
        compose.onNodeWithTag("plugin-home-query").performClick()
        compose.waitUntil(15_000) { cache.read().active!!.sections["grades"]!!.rows[0][0] == "算法设计" }
        assertEquals(1, calls.get())
        assertEquals("算法设计", PluginHomeCache(context, record.installKey).read().active!!.sections["grades"]!!.rows[0][0])
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        waitForRows()
        compose.onNodeWithText("算法设计").assertExists()
        assertEquals(1, calls.get())
        capture("cached-and-refreshed")
    }

    @Test fun switchingCachedAccountsAndFailedRefreshKeepBothAccountsSeparate() {
        show(openGrades = false)
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-accounts"))
        compose.onNodeWithTag("plugin-home-accounts").performClick()
        compose.onNodeWithTag("plugin-home-account-${beta.id}").performClick()
        compose.waitUntil(15_000) { cache.read().activeAccountId == beta.id }
        openSection("grades")
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-row-0"))
        compose.onNodeWithText("计算机网络").assertExists()
        assertEquals(0, calls.get())
        fail = true
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-query"))
        compose.onNodeWithTag("plugin-home-query").performClick()
        compose.waitUntil(15_000) { calls.get() == 1 }
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-error"))
        assertEquals("计算机网络", cache.read().active!!.sections["grades"]!!.rows[0][0])
        assertEquals("数据库原理", cache.read().accounts[alpha.id]!!.sections["grades"]!!.rows[0][0])
        capture("failed-refresh-keeps-cache")
    }

    @Test fun narrowDarkLargeTextIsUsable() {
        show(320, 1.3f, true, openGrades = false)
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-tab-credits"))
        compose.onNodeWithTag("plugin-home-tab-credits").performClick()
        compose.waitForIdle()
        backToMenu()
        openSection("grades")
        capture("narrow-dark-large-zh")
        assertEquals(0, calls.get())
    }

    @Test fun menuIsUsableOnNarrowDarkLargeTextAndEnglish() {
        show(320, 1.3f, true, openGrades = false)
        captureScreen("narrow-dark-large-menu")
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-tab-overview"))
        compose.onNodeWithTag("plugin-home-tab-overview").assertExists()
        captureScreen("narrow-dark-large-menu-bottom")
    }

    @Test fun englishMenuShowsLocalizedEntries() {
        show(language = "en", openGrades = false)
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-tab-grades"))
        compose.onNodeWithText("Grades").assertExists()
        captureScreen("english-menu")
    }

    @Test fun englishPageAndPersistentCacheAreUsable() {
        show(language = "en")
        capture("english")
        assertEquals(0, calls.get())
    }

    @Test fun traditionalChinesePageIsUsable() {
        show(320, 1.3f, false, "zh-TW")
        capture("traditional-chinese")
        assertEquals(0, calls.get())
    }

    @Test fun appLanguageWinsOverADifferentSystemLanguage() {
        show(language = "zh-CN", systemLanguage = "en", openGrades = false)
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-tab-grades"))
        compose.onNodeWithText("成绩查询").assertExists()
        compose.onNodeWithText("Grades").assertDoesNotExist()
        captureScreen("app-language-over-system-menu")
    }

    @Test fun encryptedCacheIsIsolatedByInstallSource() {
        val remote = PluginHomeCache(context, "home-ui-qa:remote")
        remote.clear()
        assertTrue(remote.read().accounts.isEmpty())
        val files = File(context.filesDir, "plugin-home-cache").listFiles().orEmpty()
        assertTrue(files.none { it.readBytes().toString(Charsets.UTF_8).contains("2099000001") })
        remote.update { it.withSection(beta, section("远端来源课程")) }
        assertEquals("数据库原理", cache.read().active!!.sections["grades"]!!.rows[0][0])
        remote.clear()
        assertEquals("数据库原理", cache.read().active!!.sections["grades"]!!.rows[0][0])
    }

    @Test fun clearSavedLoginRequiresConfirmationAndKeepsAcademicCache() {
        val credentials = WebLoginCredentialStore(context)
        credentials.save(record.pluginId, WebLoginCredential("school.example.edu", "fixture-student", "fixture-password"))
        show(width = 320, scale = 1.3f, openGrades = false)
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-clear-credentials"))
        compose.onNodeWithTag("plugin-home-clear-credentials").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("plugin-home-confirm-clear-credentials").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasText("取消") or hasText("Cancel")).performClick()
        assertTrue(credentials.hasSavedCredentials(record.pluginId))
        compose.onNodeWithTag("plugin-home-clear-credentials").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag("plugin-home-confirm-clear-credentials").fetchSemanticsNodes().isNotEmpty()
        }
        val bitmap = screenBitmap()
        File(context.filesDir, "plugin-home-qa/clear-login-confirm.png").apply { parentFile!!.mkdirs() }.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        compose.onNodeWithTag("plugin-home-confirm-clear-credentials").performClick()
        compose.onNodeWithTag("plugin-home-credentials-cleared").assertExists()
        assertFalse(credentials.hasSavedCredentials(record.pluginId))
        assertEquals("数据库原理", cache.read().accounts[alpha.id]!!.sections["grades"]!!.rows[0][0])
        assertEquals(0, calls.get())
    }

    @Test fun menuListsEverySectionAndASectionPageSearchesAndFilters() {
        val rows = (1..8).map { index ->
            listOf(if (index == 3) "数据结构" else "课程$index", "${80 + index}", "2", if (index <= 4) "2098-2099-1" else "2098-2099-2")
        }
        cache.update {
            it.withSection(alpha, PluginHomeSection("grades", listOf("课程名称", "成绩", "学分", "学期"), rows,
                savedAt = 1_790_000_000_000)).copy(activeAccountId = alpha.id)
        }
        show(openGrades = false)
        listOf("grades", "plan", "credits", "overview").forEach {
            compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-tab-$it"))
            compose.onNodeWithTag("plugin-home-tab-$it").assertExists()
        }
        // A section page replaces the menu, so the account card is not shown beside the rows.
        openSection("grades")
        compose.onAllNodesWithTag("plugin-home-credential-status").assertCountEquals(0)
        compose.onNodeWithTag("plugin-home-count").assertTextContains("8", substring = true)
        compose.onNodeWithTag("plugin-home-search").performTextInput("数据")
        compose.waitUntil(5_000) { compose.onAllNodesWithTag("plugin-home-row-2").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("plugin-home-row-0").assertCountEquals(0)
        compose.onNodeWithTag("plugin-home-count").assertTextContains("1", substring = true)
        captureScreen("section-search")
        compose.onNodeWithTag("plugin-home-search").performTextInput("不存在的关键字")
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-no-match"))
        compose.onNodeWithTag("plugin-home-no-match").assertExists()
    }

    @Test fun anUnreachableSchoolSystemIsBlamedOnTheSchoolAndKeepsSavedResults() {
        show(openGrades = false)
        failWith = java.net.SocketTimeoutException("timed out")
        compose.onNodeWithTag("plugin-home-check").performClick()
        compose.waitUntil(15_000) { calls.get() == 1 }
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-error"))
        compose.onNodeWithTag("plugin-home-error").assertTextContains("学校官方系统", substring = true)
        compose.onAllNodesWithTag("plugin-home-error-login").assertCountEquals(0)
        assertEquals("数据库原理", cache.read().active!!.sections["grades"]!!.rows[0][0])
    }

    @Test fun anExpiredLoginWithSavedCredentialsOnlyAsksForTheSmsCode() {
        WebLoginCredentialStore(context).save(record.pluginId, WebLoginCredential("school.example.edu", "fixture-student", "fixture-password"))
        show(openGrades = false)
        loggedOut = true
        compose.onNodeWithTag("plugin-home-check").performClick()
        compose.waitUntil(15_000) { calls.get() == 1 }
        compose.onNodeWithTag("plugin-home-results").performScrollToNode(hasTestTag("plugin-home-error"))
        compose.onNodeWithTag("plugin-home-error").assertTextContains("短信验证码", substring = true)
        compose.onNodeWithTag("plugin-home-error-login").assertExists()
        assertEquals("数据库原理", cache.read().active!!.sections["grades"]!!.rows[0][0])
    }

    // Opt-in release-note captures: demo data only, one test per language so each gets a fresh composition.
    private fun announcementShots(language: String, suffix: String) {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("announcementQa") == "true")
        fun tr(zh: String, tw: String, en: String) = when (language) { "en" -> en; "zh-TW" -> tw; else -> zh }
        val account = PluginHomeAccount("demo", tr("示例同学", "示例同學", "Sample Student"), "2099000001", tr("示例大学", "示例大學", "Sample University"))
        val courses = listOf(
            Triple(tr("高等数学", "高等數學", "Calculus"), "92", "5"), Triple(tr("线性代数", "線性代數", "Linear Algebra"), "88", "3"),
            Triple(tr("大学英语", "大學英語", "College English"), "95", "4"), Triple(tr("数据结构", "資料結構", "Data Structures"), "90", "4"),
            Triple(tr("操作系统", "作業系統", "Operating Systems"), "86", "3.5"), Triple(tr("计算机网络", "電腦網路", "Computer Networks"), "91", "3.5"),
        )
        val term1 = "2098-2099-1"; val term2 = "2098-2099-2"
        val grades = PluginHomeSection("grades", listOf(tr("课程名称", "課程名稱", "Course"), tr("总评成绩", "總評成績", "Score"), tr("学分", "學分", "Credits"), tr("学年学期", "學年學期", "Term")),
            courses.mapIndexed { i, c -> listOf(c.first, c.second, c.third, if (i < 3) term1 else term2) },
            listOf(PluginHomeField(tr("已修学分", "已修學分", "Credits earned"), "23"), PluginHomeField(tr("平均绩点", "平均績點", "Average GPA"), "3.8")),
            savedAt = 1_790_000_000_000)
        val plan = PluginHomeSection("plan", listOf(tr("课程名称", "課程名稱", "Course"), tr("分类", "分類", "Type"), tr("学分", "學分", "Credits"), tr("建议修读学期", "建議修讀學期", "Suggested term")),
            courses.mapIndexed { i, c -> listOf(c.first, if (i % 2 == 0) tr("必修", "必修", "Required") else tr("选修", "選修", "Elective"), c.third, "${i / 2 + 1}") },
            listOf(PluginHomeField(tr("必修学分小计", "必修學分小計", "Required credits"), "108"), PluginHomeField(tr("全程总计", "全程總計", "Total"), "175")),
            savedAt = 1_790_000_000_000)
        val credits = PluginHomeSection("credits", listOf(tr("课程类别", "課程類別", "Category"), tr("要求学分", "要求學分", "Required"), tr("已获得学分", "已獲得學分", "Earned")),
            listOf(listOf(tr("必修", "必修", "Required"), "108", "64"), listOf(tr("选修", "選修", "Elective"), "21", "9")), savedAt = 1_790_000_000_000)
        cache.clear()
        cache.update { it.withSection(account, grades).withSection(account, plan).withSection(account, credits).copy(activeAccountId = account.id) }
        WebLoginCredentialStore(context).save(record.pluginId, WebLoginCredential("school.example.edu", "demo-student", "demo-password"))
        show(language = language, openGrades = false)
        captureScreen("announce-plugin-home-menu$suffix")
        openSection("grades")
        captureScreen("announce-plugin-home-section$suffix")
    }

    @Test fun announcementShotsZh() = announcementShots("zh-CN", "")
    @Test fun announcementShotsZhTw() = announcementShots("zh-TW", ".zh-TW")
    @Test fun announcementShotsEn() = announcementShots("en", ".en")
}
