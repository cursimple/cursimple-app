package com.x500x.cursimple.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 发布说明拆成翻页公告：带图的小节一页一个亮点，其余归到最后一页。 */
class ReleaseAnnouncementTest {

    private val notes = """
        # v0.7.3 更新内容

        这一版的重点是上课提醒与调课。

        ## 上课提醒

        快上课时弹一条提醒。

        ![品牌卡片](https://example.com/notice.png)

        ## 拖动调课

        - 长按课程拖到另一天
        ![拖动调课](https://example.com/swap.png)
        ![第二张不单独成页](https://example.com/swap2.png)

        ## 修复

        - 作息表以存储为准
    """.trimIndent()

    @Test
    fun `带图的小节各成一页，按原顺序`() {
        val announcement = buildReleaseAnnouncement(notes)!!

        assertEquals(listOf("上课提醒", "拖动调课"), announcement.highlights.map { it.title })
        assertEquals("https://example.com/notice.png", announcement.highlights[0].image.url)
        assertEquals("https://example.com/swap.png", announcement.highlights[1].image.url)
        assertTrue(announcement.highlights.all { page -> page.body.none { it is ReleaseNoteBlock.Gallery } })
        assertTrue(announcement.highlights[1].body.single() is ReleaseNoteBlock.BulletItem)
    }

    @Test
    fun `引言和不带图的小节归到最后一页，一级标题不重复`() {
        val rest = buildReleaseAnnouncement(notes)!!.rest

        assertTrue(rest.first() is ReleaseNoteBlock.Paragraph)
        val headings = rest.filterIsInstance<ReleaseNoteBlock.Heading>().map { h -> h.spans.joinToString("") { it.text } }
        assertEquals(listOf("修复"), headings)
    }

    @Test
    fun `一张图都没有的旧公告不拆页`() {
        assertNull(buildReleaseAnnouncement("# v0.7.2\n\n## 修复\n\n- 一些问题"))
    }
}
