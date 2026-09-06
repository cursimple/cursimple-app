package com.x500x.cursimple.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private fun textOf(block: ReleaseNoteBlock): String = when (block) {
    is ReleaseNoteBlock.Heading -> block.spans.joinToString("") { it.text }
    is ReleaseNoteBlock.Paragraph -> block.spans.joinToString("") { it.text }
    is ReleaseNoteBlock.BulletItem -> block.spans.joinToString("") { it.text }
    ReleaseNoteBlock.Divider -> "---"
}

class ParseReleaseNotesTest {
    @Test
    fun `headings keep their level`() {
        val blocks = parseReleaseNotes("## 课简 v0.7.0\n\n### 新增")

        assertEquals(2, blocks.size)
        assertEquals(2, (blocks[0] as ReleaseNoteBlock.Heading).level)
        assertEquals("课简 v0.7.0", textOf(blocks[0]))
        assertEquals(3, (blocks[1] as ReleaseNoteBlock.Heading).level)
    }

    @Test
    fun `bullets drop their marker`() {
        val blocks = parseReleaseNotes("- 第一条\n- 第二条")

        assertEquals(2, blocks.size)
        assertTrue(blocks.all { it is ReleaseNoteBlock.BulletItem })
        assertEquals("第一条", textOf(blocks[0]))
        assertEquals("第二条", textOf(blocks[1]))
    }

    @Test
    fun `numbered items are treated as bullets too`() {
        val blocks = parseReleaseNotes("1. 打开设置\n2. 点检查更新")

        assertEquals(2, blocks.size)
        assertEquals("打开设置", textOf(blocks[0]))
    }

    @Test
    fun `nested bullets record their indent`() {
        val blocks = parseReleaseNotes("- 顶层\n  - 次级")

        assertEquals(0, (blocks[0] as ReleaseNoteBlock.BulletItem).indent)
        assertEquals(1, (blocks[1] as ReleaseNoteBlock.BulletItem).indent)
    }

    @Test
    fun `a wrapped bullet joins without an extra space between Chinese`() {
        val blocks = parseReleaseNotes("- 关闭测试版更新后再检查，\n  会提示可以回退。")

        assertEquals(1, blocks.size)
        assertEquals("关闭测试版更新后再检查，会提示可以回退。", textOf(blocks[0]))
    }

    @Test
    fun `a wrapped latin line keeps one space`() {
        val blocks = parseReleaseNotes("- the update dialog\n  now renders markdown")

        assertEquals("the update dialog now renders markdown", textOf(blocks[0]))
    }

    @Test
    fun `a blank line ends the paragraph`() {
        val blocks = parseReleaseNotes("第一段。\n\n第二段。")

        assertEquals(2, blocks.size)
        assertEquals("第一段。", textOf(blocks[0]))
        assertEquals("第二段。", textOf(blocks[1]))
    }

    @Test
    fun `a horizontal rule becomes a divider`() {
        val blocks = parseReleaseNotes("上面\n\n---\n\n下面")

        assertEquals(3, blocks.size)
        assertEquals(ReleaseNoteBlock.Divider, blocks[1])
    }

    @Test
    fun `empty input yields nothing`() {
        assertTrue(parseReleaseNotes("").isEmpty())
        assertTrue(parseReleaseNotes("   \n\n  ").isEmpty())
    }
}

class ParseInlineTest {
    @Test
    fun `bold segments are marked and the markers removed`() {
        val spans = parseInline("**更新公告**。装完新版本后展示。")

        assertEquals(2, spans.size)
        assertEquals(ReleaseNoteSpan("更新公告", bold = true), spans[0])
        assertEquals("。装完新版本后展示。", spans[1].text)
    }

    @Test
    fun `inline code is marked`() {
        val spans = parseInline("改动在 `update.json` 里")

        assertEquals(3, spans.size)
        assertTrue(spans[1].code)
        assertEquals("update.json", spans[1].text)
    }

    @Test
    fun `a markdown link keeps its label and target`() {
        val spans = parseInline("详见 [发布页](https://example.com/r/1) 说明")

        val link = spans.single { it.link != null }
        assertEquals("发布页", link.text)
        assertEquals("https://example.com/r/1", link.link)
    }

    @Test
    fun `a bare url becomes a link to itself`() {
        val spans = parseInline("https://example.com/compare")

        assertEquals(1, spans.size)
        assertEquals("https://example.com/compare", spans[0].link)
    }

    @Test
    fun `plain text stays one span`() {
        val spans = parseInline("没有任何标记")

        assertEquals(listOf(ReleaseNoteSpan("没有任何标记")), spans)
    }

    @Test
    fun `an unmatched asterisk is left alone`() {
        val spans = parseInline("2 * 3 = 6")

        assertEquals("2 * 3 = 6", spans.joinToString("") { it.text })
    }
}
