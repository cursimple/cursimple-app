package com.x500x.cursimple.feature.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MemoEditingTest {

    private fun ids(): () -> Long {
        var next = 0L
        return { ++next }
    }

    private fun lines(body: String) = parseMemoLines(body, ids())

    @Test
    fun parseAndSerialize_roundTripsAndRenumbers() {
        val parsed = lines("# 复习\n- [x] 第一章\n- [ ] 第二章\n- 带笔\n3. 甲\n7) 乙\n> 别忘了\n普通")
        assertEquals(
            listOf(
                MemoLineKind.Heading, MemoLineKind.Check, MemoLineKind.Check, MemoLineKind.Bullet,
                MemoLineKind.Numbered, MemoLineKind.Numbered, MemoLineKind.Quote, MemoLineKind.Plain,
            ),
            parsed.map { it.kind },
        )
        assertEquals(listOf("复习", "第一章", "第二章", "带笔", "甲", "乙", "别忘了", "普通"), parsed.map { it.content })
        assertEquals(true, parsed[1].checked)
        // 编号存回去时按 1、2 重新数
        assertEquals("# 复习\n- [x] 第一章\n- [ ] 第二章\n- 带笔\n1. 甲\n2. 乙\n> 别忘了\n普通", serializeMemoLines(parsed))
        // 缩进留着
        assertEquals("  - [ ] 子项", serializeMemoLines(lines("  - [ ] 子项")))
    }

    @Test
    fun numbers_restartAfterOtherLines() {
        val numbers = memoLineNumbers(lines("1. a\n2. b\n中间\n5. c"))
        assertEquals(listOf(1, 2, 0, 1), numbers.toList())
    }

    @Test
    fun enter_continuesFormatAndExitsOnEmptyItem() {
        val newId = ids()
        val start = parseMemoLines("- [x] 已做", newId)
        val split = memoInsertLineBreaks(start, 0, "已做\n", 3, newId)
        assertEquals("- [x] 已做\n- [ ] ", serializeMemoLines(split.lines))
        assertEquals(split.lines[1].id, split.focusId)
        assertEquals(0, split.cursor)

        // 在中间回车：后半截带到下一行
        val middle = memoInsertLineBreaks(parseMemoLines("- 甲乙", newId), 0, "甲\n乙", 2, newId)
        assertEquals("- 甲\n- 乙", serializeMemoLines(middle.lines))

        // 标题后面接普通文字
        val heading = memoInsertLineBreaks(parseMemoLines("# 标题", newId), 0, "标题\n", 3, newId)
        assertEquals(MemoLineKind.Plain, heading.lines[1].kind)

        // 空的列表项上回车：变回普通文字，不多出一行
        val exit = memoInsertLineBreaks(parseMemoLines("- 甲\n- ", newId), 1, "\n", 1, newId)
        assertEquals("- 甲\n", serializeMemoLines(exit.lines))
        assertEquals(2, exit.lines.size)
    }

    @Test
    fun paste_parsesEachLine() {
        val newId = ids()
        val pasted = "清单\n- [ ] 买书\n1. 交作业"
        val result = memoInsertLineBreaks(parseMemoLines("", newId), 0, pasted, pasted.length, newId)
        assertEquals(listOf(MemoLineKind.Plain, MemoLineKind.Check, MemoLineKind.Numbered), result.lines.map { it.kind })
        assertEquals(result.lines.last().id, result.focusId)
        assertEquals("交作业".length, result.cursor)
    }

    @Test
    fun backspaceAtStart_dropsFormatThenMerges() {
        val start = lines("第一行\n- [ ] 第二行")
        val plain = memoBackspaceAtStart(start, 1)!!
        assertEquals("第一行\n第二行", serializeMemoLines(plain.lines))
        val merged = memoBackspaceAtStart(plain.lines, 1)!!
        assertEquals("第一行第二行", serializeMemoLines(merged.lines))
        assertEquals(merged.lines[0].id, merged.focusId)
        assertEquals(3, merged.cursor)
        assertNull(memoBackspaceAtStart(merged.lines, 0))
    }

    @Test
    fun shortcuts_turnTypedPrefixIntoFormat() {
        val line = MemoLine(1, content = "- [ ] 买书")
        val (check, removed) = memoApplyShortcut(line)!!
        assertEquals(MemoLineKind.Check, check.kind)
        assertEquals("买书", check.content)
        assertEquals(6, removed)
        // 先敲「- 」变成列表，再敲「[ ] 」变成复选框
        val (fromBullet, _) = memoApplyShortcut(MemoLine(2, MemoLineKind.Bullet, "[ ] "))!!
        assertEquals(MemoLineKind.Check, fromBullet.kind)
        assertEquals(MemoLineKind.Heading, memoApplyShortcut(MemoLine(3, content = "# "))!!.first.kind)
        // 「>」后面没打空格先不变，不然打不出「>=」
        assertNull(memoApplyShortcut(MemoLine(4, content = ">=")))
        assertNull(memoApplyShortcut(MemoLine(5, content = "普通文字")))
        assertNull(memoApplyShortcut(MemoLine(6, MemoLineKind.Check, "- 不再转")))
    }

    @Test
    fun toggleKind_addsSwapsAndRemoves() {
        val plain = MemoLine(1, content = "交作业")
        val check = memoToggleKind(plain, MemoLineKind.Check)
        assertEquals(MemoLineKind.Check, check.kind)
        assertEquals(MemoLineKind.Bullet, memoToggleKind(check, MemoLineKind.Bullet).kind)
        assertEquals(MemoLineKind.Plain, memoToggleKind(check, MemoLineKind.Check).kind)
    }

    @Test
    fun wrap_selectionOrEmptyPair() {
        val sel = wrapSelection(MemoTextState("重点内容", 0, 2), "**")
        assertEquals("**重点**内容", sel.text)
        assertEquals(2, sel.selectionStart)
        assertEquals(4, sel.selectionEnd)
        val none = wrapSelection(MemoTextState("ab", 1), "**")
        assertEquals("a****b", none.text)
        assertEquals(3, none.selectionStart)
    }
}
