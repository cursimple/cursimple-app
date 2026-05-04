package com.kebiao.viewer.core.plugin.runtime

import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.install.PluginInstallSource
import com.kebiao.viewer.core.plugin.manifest.PluginPermission
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.core.plugin.web.WebSessionCaptureSpec
import com.kebiao.viewer.core.plugin.web.WebSessionPacket
import com.kebiao.viewer.core.plugin.workflow.WorkflowDefinition
import com.kebiao.viewer.core.plugin.workflow.WorkflowStepDefinition
import com.kebiao.viewer.core.plugin.workflow.WorkflowStepType
import kotlinx.coroutines.runBlocking
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class WorkflowEngineTemplateTest {
    @Test
    fun `start renders double brace templates without regex crash`() = runBlocking {
        val engine = DefaultWorkflowEngine()
        val bundle = InstalledPluginBundle(
            record = InstalledPluginRecord(
                pluginId = "yangtzeu-eams-v2",
                name = "长江大学教务插件",
                publisher = "Class Schedule Viewer",
                version = "2.0.0",
                versionCode = 3001,
                storagePath = "unused",
                installedAt = "2026-04-30T00:00:00+08:00",
                source = PluginInstallSource.Bundled,
                declaredPermissions = listOf(PluginPermission.WebSession),
                allowedHosts = listOf("example.com"),
                isBundled = true,
            ),
            workflow = WorkflowDefinition(
                steps = listOf(
                    WorkflowStepDefinition(
                        id = "message",
                        type = WorkflowStepType.Message,
                        message = "正在为 {{pluginId}} 打开 {{username}} 的登录页",
                    ),
                    WorkflowStepDefinition(
                        id = "web-login",
                        type = WorkflowStepType.WebSession,
                        title = "登录",
                        urlTemplate = "https://example.com/login?user={{username}}",
                        autoNavigateOnUrlContains = "/home.action",
                        autoNavigateToUrl = "https://example.com/eams/courseTableForStd.action?user={{username}}",
                        userAgent = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36",
                        capturePackets = listOf(
                            WebSessionCaptureSpec(
                                id = "login-ready",
                                urlHost = "example.com",
                                captureSelectors = listOf("title"),
                            ),
                        ),
                    ),
                ),
            ),
            uiSchema = PluginUiSchema(),
            timingProfile = null,
        )

        val result = engine.start(
            bundle = bundle,
            input = PluginSyncInput(
                pluginId = "yangtzeu-eams-v2",
                username = "20260001",
                password = "",
                termId = "",
                baseUrl = "",
            ),
            assetReader = { error("不应该读取资源文件: $it") },
        )

        assertTrue(result is WorkflowExecutionResult.AwaitingWebSession)
        val awaiting = result as WorkflowExecutionResult.AwaitingWebSession
        assertEquals("正在为 yangtzeu-eams-v2 打开 20260001 的登录页", awaiting.messages.single())
        assertEquals("https://example.com/login?user=20260001", awaiting.request.startUrl)
        assertEquals("/home.action", awaiting.request.autoNavigateOnUrlContains)
        assertEquals("https://example.com/eams/courseTableForStd.action?user=20260001", awaiting.request.autoNavigateToUrl)
        assertEquals(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36",
            awaiting.request.userAgent,
        )
        assertEquals("login-ready", awaiting.request.capturePackets.single().id)
    }

    @Test
    fun `http request can repeat over rendered numeric range`() = runBlocking {
        val requestBodies = mutableListOf<String>()
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val buffer = Buffer()
                chain.request().body?.writeTo(buffer)
                requestBodies += buffer.readUtf8()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody("text/plain; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()
        val engine = DefaultWorkflowEngine(client = client)
        val bundle = InstalledPluginBundle(
            record = InstalledPluginRecord(
                pluginId = "yangtzeu-eams-v2",
                name = "长江大学教务插件",
                publisher = "Class Schedule Viewer",
                version = "2.0.0",
                versionCode = 3001,
                storagePath = "unused",
                installedAt = "2026-04-30T00:00:00+08:00",
                source = PluginInstallSource.Bundled,
                declaredPermissions = listOf(PluginPermission.Network),
                allowedHosts = listOf("example.com"),
                isBundled = true,
            ),
            workflow = WorkflowDefinition(
                steps = listOf(
                    WorkflowStepDefinition(
                        id = "weeks",
                        type = WorkflowStepType.HttpRequest,
                        responseKey = "detail",
                        httpMethod = "POST",
                        httpContentType = "application/x-www-form-urlencoded; charset=UTF-8",
                        urlTemplate = "https://example.com/eams/courseTableForStd!courseTable.action?sf_request_type=ajax",
                        httpBodyTemplate = "startWeek={{context.week}}",
                        httpRepeatStart = 1,
                        httpRepeatEndTemplate = "3",
                        httpRepeatVariable = "week",
                    ),
                ),
            ),
            uiSchema = PluginUiSchema(),
            timingProfile = null,
        )

        val result = engine.start(
            bundle = bundle,
            input = PluginSyncInput(
                pluginId = "yangtzeu-eams-v2",
                username = "",
                password = "",
                termId = "",
                baseUrl = "",
            ),
            assetReader = { error("不应该读取资源文件: $it") },
        )

        assertTrue(result is WorkflowExecutionResult.Failure)
        assertEquals(listOf("startWeek=1", "startWeek=2", "startWeek=3"), requestBodies)
    }

    @Test
    fun `http request retries matching response body before next repeat value`() = runBlocking {
        val requestBodies = mutableListOf<String>()
        var firstWeekAttempts = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val buffer = Buffer()
                chain.request().body?.writeTo(buffer)
                val requestBody = buffer.readUtf8()
                requestBodies += requestBody
                val responseText = if (requestBody == "startWeek=1") {
                    firstWeekAttempts += 1
                    if (firstWeekAttempts == 1) {
                        "<span>请不要过快点击</span>"
                    } else {
                        "ok-week-1"
                    }
                } else {
                    "ok-week-2"
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(responseText.toResponseBody("text/plain; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()
        val engine = DefaultWorkflowEngine(client = client)
        val bundle = InstalledPluginBundle(
            record = InstalledPluginRecord(
                pluginId = "yangtzeu-eams-v2",
                name = "长江大学教务插件",
                publisher = "Class Schedule Viewer",
                version = "2.0.0",
                versionCode = 3001,
                storagePath = "unused",
                installedAt = "2026-04-30T00:00:00+08:00",
                source = PluginInstallSource.Bundled,
                declaredPermissions = listOf(PluginPermission.Network),
                allowedHosts = listOf("example.com"),
                isBundled = true,
            ),
            workflow = WorkflowDefinition(
                steps = listOf(
                    WorkflowStepDefinition(
                        id = "weeks",
                        type = WorkflowStepType.HttpRequest,
                        responseKey = "detail",
                        httpMethod = "POST",
                        httpContentType = "application/x-www-form-urlencoded; charset=UTF-8",
                        urlTemplate = "https://example.com/eams/courseTableForStd!courseTable.action?sf_request_type=ajax",
                        httpBodyTemplate = "startWeek={{context.week}}",
                        httpRepeatStart = 1,
                        httpRepeatEndTemplate = "2",
                        httpRepeatVariable = "week",
                        httpRetryBodyContains = "请不要过快点击",
                        httpRetryDelayMillis = 1,
                    ),
                ),
            ),
            uiSchema = PluginUiSchema(),
            timingProfile = null,
        )

        val result = engine.start(
            bundle = bundle,
            input = PluginSyncInput(
                pluginId = "yangtzeu-eams-v2",
                username = "",
                password = "",
                termId = "",
                baseUrl = "",
            ),
            assetReader = { error("不应该读取资源文件: $it") },
        )

        assertTrue(result is WorkflowExecutionResult.Failure)
        assertEquals(listOf("startWeek=1", "startWeek=1", "startWeek=2"), requestBodies)
    }

    @Test
    fun `http fresh connection creates client per repeated request and keeps explicit cookie`() = runBlocking {
        val requestBodies = mutableListOf<String>()
        val connectionHeaders = mutableListOf<String?>()
        val cookieHeaders = mutableListOf<String?>()
        var freshClientCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val buffer = Buffer()
                chain.request().body?.writeTo(buffer)
                requestBodies += buffer.readUtf8()
                connectionHeaders += chain.request().header("Connection")
                cookieHeaders += chain.request().header("Cookie")
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("ok".toResponseBody("text/plain; charset=utf-8".toMediaType()))
                    .build()
            }
            .build()
        val engine = DefaultWorkflowEngine(
            client = client,
            freshClientFactory = { baseClient ->
                freshClientCount += 1
                baseClient.newBuilder()
                    .connectionPool(ConnectionPool(0, 1, TimeUnit.NANOSECONDS))
                    .build()
            },
        )
        val bundle = InstalledPluginBundle(
            record = InstalledPluginRecord(
                pluginId = "yangtzeu-eams-v2",
                name = "长江大学教务插件",
                publisher = "Class Schedule Viewer",
                version = "2.0.0",
                versionCode = 3001,
                storagePath = "unused",
                installedAt = "2026-04-30T00:00:00+08:00",
                source = PluginInstallSource.Bundled,
                declaredPermissions = listOf(PluginPermission.WebSession, PluginPermission.Network),
                allowedHosts = listOf("example.com"),
                isBundled = true,
            ),
            workflow = WorkflowDefinition(
                steps = listOf(
                    WorkflowStepDefinition(
                        id = "web-login",
                        type = WorkflowStepType.WebSession,
                        sessionId = "login",
                        title = "登录",
                        urlTemplate = "https://example.com/login",
                    ),
                    WorkflowStepDefinition(
                        id = "weeks",
                        type = WorkflowStepType.HttpRequest,
                        responseKey = "detail",
                        cookieSessionId = "login",
                        httpFreshConnection = true,
                        httpMethod = "POST",
                        httpContentType = "application/x-www-form-urlencoded; charset=UTF-8",
                        urlTemplate = "https://example.com/eams/courseTableForStd!courseTable.action?sf_request_type=ajax",
                        httpBodyTemplate = "startWeek={{context.week}}",
                        httpRepeatStart = 1,
                        httpRepeatEndTemplate = "2",
                        httpRepeatVariable = "week",
                    ),
                ),
            ),
            uiSchema = PluginUiSchema(),
            timingProfile = null,
        )

        val awaiting = engine.start(
            bundle = bundle,
            input = PluginSyncInput(
                pluginId = "yangtzeu-eams-v2",
                username = "",
                password = "",
                termId = "",
                baseUrl = "",
            ),
            assetReader = { error("不应该读取资源文件: $it") },
        ) as WorkflowExecutionResult.AwaitingWebSession
        val result = engine.resume(
            token = awaiting.request.token,
            packet = WebSessionPacket(
                finalUrl = "https://example.com/eams/courseTableForStd.action",
                cookies = mapOf("sid" to "abc", "route" to "1"),
                timestamp = "2026-05-04T00:00:00+08:00",
            ),
            assetReader = { _, path -> error("不应该读取资源文件: $path") },
        )

        assertTrue(result is WorkflowExecutionResult.Failure)
        assertEquals(2, freshClientCount)
        assertEquals(listOf("startWeek=1", "startWeek=2"), requestBodies)
        assertEquals(listOf("close", "close"), connectionHeaders)
        assertEquals(listOf("sid=abc; route=1", "sid=abc; route=1"), cookieHeaders)
    }

    @Test
    fun `eams parser failure returns workflow failure instead of throwing`() = runBlocking {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(
                        """
                        <html>
                          <script>
                            beangle.form.submit("searchForm");
                          </script>
                        </html>
                        """.trimIndent().toResponseBody("text/html; charset=utf-8".toMediaType()),
                    )
                    .build()
            }
            .build()
        val engine = DefaultWorkflowEngine(client = client)
        val bundle = InstalledPluginBundle(
            record = InstalledPluginRecord(
                pluginId = "yangtzeu-eams-v2",
                name = "长江大学教务插件",
                publisher = "Class Schedule Viewer",
                version = "2.0.0",
                versionCode = 3001,
                storagePath = "unused",
                installedAt = "2026-04-30T00:00:00+08:00",
                source = PluginInstallSource.Bundled,
                declaredPermissions = listOf(PluginPermission.Network),
                allowedHosts = listOf("jwc3.yangtzeu.edu.cn"),
                isBundled = true,
            ),
            workflow = WorkflowDefinition(
                steps = listOf(
                    WorkflowStepDefinition(
                        id = "course-home",
                        type = WorkflowStepType.HttpRequest,
                        responseKey = "courseHome",
                        httpMethod = "GET",
                        urlTemplate = "https://jwc3.yangtzeu.edu.cn/eams/courseTableForStd.action",
                    ),
                    WorkflowStepDefinition(
                        id = "course-meta",
                        type = WorkflowStepType.EamsExtractMeta,
                        sourceContextKey = "courseHome",
                        responseKey = "courseMeta",
                    ),
                ),
            ),
            uiSchema = PluginUiSchema(),
            timingProfile = null,
        )

        val result = engine.start(
            bundle = bundle,
            input = PluginSyncInput(
                pluginId = "yangtzeu-eams-v2",
                username = "",
                password = "",
                termId = "",
                baseUrl = "",
            ),
            assetReader = { error("不应该读取资源文件: $it") },
        )

        assertTrue(result is WorkflowExecutionResult.Failure)
        assertEquals("未找到教学周上限", (result as WorkflowExecutionResult.Failure).message)
    }
}
