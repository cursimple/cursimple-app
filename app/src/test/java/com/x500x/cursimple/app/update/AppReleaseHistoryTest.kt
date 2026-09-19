package com.x500x.cursimple.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 版本历史的过滤规则。
 *
 * 「接收测试版更新」关着的人不该在历史里看到 beta：他装不到，列出来只会让人以为漏了更新。
 */
class AppReleaseHistoryTest {

    private val body = """
        [
          {"tag_name":"v0.7.1-beta.9","name":"beta 9","body":"测试版内容","prerelease":true,
           "draft":false,"published_at":"2026-09-19T20:56:38Z","html_url":"https://x/9"},
          {"tag_name":"v0.7.0","name":"正式版","body":"正式内容","prerelease":false,
           "draft":false,"published_at":"2026-09-01T00:00:00Z","html_url":"https://x/7"},
          {"tag_name":"v0.8.0","name":"草稿","body":"没发出去","prerelease":false,
           "draft":true,"published_at":"","html_url":"https://x/8"}
        ]
    """.trimIndent()

    @Test
    fun `开着测试版时正式版与测试版都列出来`() {
        val history = parseReleaseHistory(body, includePrerelease = true)

        assertEquals(listOf("v0.7.1-beta.9", "v0.7.0"), history.map { it.tagName })
    }

    @Test
    fun `关掉测试版时只剩正式版`() {
        val history = parseReleaseHistory(body, includePrerelease = false)

        assertEquals(listOf("v0.7.0"), history.map { it.tagName })
    }

    @Test
    fun `草稿一律不列`() {
        val history = parseReleaseHistory(body, includePrerelease = true)

        assertTrue(history.none { it.tagName == "v0.8.0" })
    }

    @Test
    fun `更新内容跟着列表一起带回来，不用再发一次请求`() {
        val history = parseReleaseHistory(body, includePrerelease = true)

        assertEquals("测试版内容", history.first().notes)
    }

    @Test
    fun `没有名字时退回用标签名`() {
        val history = parseReleaseHistory(
            """[{"tag_name":"v1.0.0","name":"","body":"","prerelease":false,"draft":false}]""",
            includePrerelease = false,
        )

        assertEquals("v1.0.0", history.single().name)
    }

    @Test
    fun `坏数据不会让整份历史崩掉`() {
        assertEquals(emptyList<AppReleaseSummary>(), parseReleaseHistory("不是 JSON", includePrerelease = true))
        // 缺 tag 的那条跳过，其余照常
        val mixed = parseReleaseHistory(
            """[{"name":"缺标签"},{"tag_name":"v1.0.0","prerelease":false,"draft":false}]""",
            includePrerelease = false,
        )
        assertEquals(listOf("v1.0.0"), mixed.map { it.tagName })
    }
}
