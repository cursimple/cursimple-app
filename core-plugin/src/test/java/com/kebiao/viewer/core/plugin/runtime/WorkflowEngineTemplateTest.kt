package com.kebiao.viewer.core.plugin.runtime

import com.kebiao.viewer.core.plugin.install.InstalledPluginRecord
import com.kebiao.viewer.core.plugin.install.PluginInstallSource
import com.kebiao.viewer.core.plugin.manifest.PluginPermission
import com.kebiao.viewer.core.plugin.ui.PluginUiSchema
import com.kebiao.viewer.core.plugin.web.WebSessionCaptureSpec
import com.kebiao.viewer.core.plugin.workflow.WorkflowDefinition
import com.kebiao.viewer.core.plugin.workflow.WorkflowStepDefinition
import com.kebiao.viewer.core.plugin.workflow.WorkflowStepType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals(
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36",
            awaiting.request.userAgent,
        )
        assertEquals("login-ready", awaiting.request.capturePackets.single().id)
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
