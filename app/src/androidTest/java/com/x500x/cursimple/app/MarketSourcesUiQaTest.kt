package com.x500x.cursimple.app

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.x500x.cursimple.R
import com.x500x.cursimple.app.github.GitHubAccountStore
import com.x500x.cursimple.app.theme.ClassScheduleTheme
import com.x500x.cursimple.core.data.ThemeMode
import com.x500x.cursimple.core.plugin.market.github.DefaultMarketSources
import com.x500x.cursimple.core.plugin.market.github.GitHubRegistryRepository
import com.x500x.cursimple.core.plugin.market.github.GitHubApiClient
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MarketSourcesUiQaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun sourcesCanBeRemovedRestoredAndValidateDuplicateLinks() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("marketSourcesQa") == "true")
        val context = compose.activity
        val pluginSources = mutableStateOf(listOf(DefaultMarketSources.PLUGIN_REGISTRY))
        val componentSources = mutableStateOf(listOf(DefaultMarketSources.COMPONENT_REGISTRY))
        val registry = GitHubRegistryRepository(fetchText = {
            """{"repositories":[{"name":"qa/sample","repo":"sample","owner":"qa","description":"测试条目","star":1}]}"""
        })
        val account = GitHubAccountStore(IsolatedGitHubContext(context, "market_sources_ui_qa"))
        account.clear()
        val services = MarketSourceServices(registry, account, oauthClientId = "")
        compose.runOnUiThread {
            context.setContent {
                ClassScheduleTheme(ThemeMode.Light) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            MarketSourceSettings(pluginSources.value, componentSources.value,
                                { pluginSources.value = it }, { componentSources.value = it }, services)
                        }
                    }
                }
            }
        }
        compose.waitForIdle()
        shot("sources")
        compose.onAllNodesWithContentDescription(context.getString(R.string.market_sources_remove))[0].performClick()
        compose.onNodeWithText(context.getString(R.string.market_sources_remove_confirm_title)).assertIsDisplayed()
        assertEquals(listOf(DefaultMarketSources.PLUGIN_REGISTRY), pluginSources.value)
        compose.onNodeWithText(context.getString(R.string.settings_cancel)).performClick()
        assertEquals(listOf(DefaultMarketSources.PLUGIN_REGISTRY), pluginSources.value)
        compose.onAllNodesWithContentDescription(context.getString(R.string.market_sources_remove))[0].performClick()
        compose.onNodeWithText(context.getString(R.string.market_sources_remove_confirm)).performClick()
        compose.onNodeWithText(context.getString(R.string.market_sources_empty)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.market_sources_restore_default)).performClick()
        compose.onAllNodesWithContentDescription(context.getString(R.string.market_sources_remove))[0].assertIsDisplayed()
        compose.onAllNodesWithText(context.getString(R.string.market_sources_add))[0].performClick()
        compose.onNodeWithText(context.getString(R.string.market_sources_add_label)).performTextInput("https://github.com/cursimple/cursimple-plugins.git/tree/main")
        compose.onNodeWithText(context.getString(R.string.market_sources_add_duplicate)).assertIsDisplayed()
        shot("duplicate-source")
        compose.onNodeWithText(context.getString(R.string.settings_cancel)).performClick()
        compose.onNodeWithText(context.getString(R.string.github_account_login_token)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.github_token_steps)).assertIsDisplayed()
        shot("github-login")
        compose.onNodeWithText(context.getString(R.string.settings_cancel)).performClick()
        account.clear()
    }

    @Test fun everySourceRequiresConfirmationIncludingPublicAndCustomComponents() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("marketSourcesQa") == "true")
        val context = compose.activity
        val pluginSources = mutableStateOf(listOf(DefaultMarketSources.PLUGIN_REGISTRY, "qa/private-plugins"))
        val componentSources = mutableStateOf(listOf(DefaultMarketSources.COMPONENT_REGISTRY, "qa/private-components"))
        val api = GitHubApiClient(client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(404).message("test fixture").body("{}".toResponseBody()).build()
        }.build())
        val registry = GitHubRegistryRepository(apiClient = api, fetchText = { """{"repositories":[]}""" })
        val account = GitHubAccountStore(IsolatedGitHubContext(context, "market_sources_confirmation_qa"))
        account.clear()
        val services = MarketSourceServices(registry, account, oauthClientId = "")
        compose.runOnUiThread {
            context.setContent {
                ClassScheduleTheme(ThemeMode.Light) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            MarketSourceSettings(pluginSources.value, componentSources.value,
                                { pluginSources.value = it }, { componentSources.value = it }, services)
                        }
                    }
                }
            }
        }
        val targets = listOf("qa/private-plugins", DefaultMarketSources.COMPONENT_REGISTRY, "qa/private-components")
        for (target in targets) {
            fun removeIndex(): Int = pluginSources.value.indexOf(target).takeIf { it >= 0 }
                ?: (pluginSources.value.size + componentSources.value.indexOf(target))
            val pluginsBefore = pluginSources.value
            val componentsBefore = componentSources.value
            compose.onAllNodesWithContentDescription(context.getString(R.string.market_sources_remove))[removeIndex()]
                .performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.market_sources_remove_confirm_title)).assertIsDisplayed()
            assertEquals(pluginsBefore, pluginSources.value)
            assertEquals(componentsBefore, componentSources.value)
            compose.onNodeWithText(context.getString(R.string.settings_cancel)).performClick()
            assertEquals(pluginsBefore, pluginSources.value)
            assertEquals(componentsBefore, componentSources.value)
            compose.onAllNodesWithContentDescription(context.getString(R.string.market_sources_remove))[removeIndex()]
                .performScrollTo().performClick()
            compose.onNodeWithText(context.getString(R.string.market_sources_remove_confirm)).performClick()
            assertEquals(pluginsBefore.filterNot { it == target }, pluginSources.value)
            assertEquals(componentsBefore.filterNot { it == target }, componentSources.value)
        }
        account.clear()
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val painted = CountDownLatch(1)
        instrumentation.runOnMainSync {
            val view = compose.activity.window.decorView
            view.postOnAnimation { view.postOnAnimation { painted.countDown() } }
        }
        check(painted.await(5, TimeUnit.SECONDS)) { "Source settings did not paint" }
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), "market-qa/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }
}

internal class IsolatedGitHubContext(base: Context, private val name: String) : ContextWrapper(base) {
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(ignored: String, mode: Int) = super.getSharedPreferences(name, mode)
}
