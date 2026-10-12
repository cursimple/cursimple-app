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
    fun parseAndSerialize_roundTripsAndKeepsListStart() {
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
        assertEquals("# 复习\n- [x] 第一章\n- [ ] 第二章\n- 带笔\n3. 甲\n4. 乙\n> 别忘了\n普通", serializeMemoLines(parsed))
        assertEquals("  - [ ] 子项", serializeMemoLines(lines("  - [ ] 子项")))
    }

    @Test
    fun numbers_countUpWithinRunAndKeepTypedStart() {
        val numbers = memoLineNumbers(lines("1. a\n7. b\n中间\n5. c\n  1. sub\n6. d"))
        assertEquals(listOf(1, 2, 0, 5, 1, 6), numbers.toList())
    }

    @Test
    fun enter_isPlainLineBreakWithoutMarkers() {
        val newId = ids()
        val start = parseMemoLines("2. 已做", newId)
        val split = memoInsertLineBreaks(start, 0, "已做\n", 3, newId)
        assertEquals("2. 已做\n", serializeMemoLines(split.lines))
        assertEquals(MemoLineKind.Plain, split.lines[1].kind)
        assertEquals(split.lines[1].id, split.focusId)
        assertEquals(0, split.cursor)

        val middle = memoInsertLineBreaks(parseMemoLines("- [ ] 甲乙", newId), 0, "甲\n乙", 2, newId)
        assertEquals("- [ ] 甲\n乙", serializeMemoLines(middle.lines))

        val heading = memoInsertLineBreaks(parseMemoLines("# 标题", newId), 0, "标题\n", 3, newId)
        assertEquals(MemoLineKind.Plain, heading.lines[1].kind)

        // Text after the caret is not reinterpreted as Markdown.
        val literal = memoInsertLineBreaks(parseMemoLines("看 1. 这里", newId), 0, "看 \n1. 这里", 3, newId)
        assertEquals(MemoLineKind.Plain, literal.lines[1].kind)
        assertEquals("1. 这里", literal.lines[1].content)

        val empty = memoInsertLineBreaks(parseMemoLines("- 甲\n- ", newId), 1, "\n", 1, newId)
        assertEquals("- 甲\n- \n", serializeMemoLines(empty.lines))
    }

    @Test
    fun enter_atStartOfFormattedLineOpensBlankAbove() {
        val newId = ids()
        val start = parseMemoLines("1. 甲", newId)
        val result = memoInsertLineBreaks(start, 0, "\n甲", 1, newId)
        assertEquals("\n1. 甲", serializeMemoLines(result.lines))
        assertEquals(start[0].id, result.focusId)
        assertEquals(0, result.cursor)
    }

    @Test
    fun toolbarNumbered_continuesFromItemAbove() {
        val list = lines("1. 甲\n2. 乙\n丙")
        val numbered = memoToolbarKind(list, 2, MemoLineKind.Numbered)
        assertEquals(3, numbered.number)
        assertEquals("1. 甲\n2. 乙\n3. 丙", serializeMemoLines(list.toMutableList().also { it[2] = numbered }))

        // A paragraph between items still continues; a blank line or heading restarts.
        assertEquals(3, memoToolbarKind(lines("1. 甲\n2. 乙\n说明\n丁"), 3, MemoLineKind.Numbered).number)
        assertEquals(1, memoToolbarKind(lines("1. 甲\n\n丁"), 2, MemoLineKind.Numbered).number)
        assertEquals(1, memoToolbarKind(lines("1. 甲\n# 新节\n丁"), 2, MemoLineKind.Numbered).number)
        assertEquals(6, memoToolbarKind(lines("5. 甲\n丁"), 1, MemoLineKind.Numbered).number)

        // Tapping again removes it; the next item keeps its own number.
        val again = memoToolbarKind(list.toMutableList().also { it[2] = numbered }, 2, MemoLineKind.Numbered)
        assertEquals(MemoLineKind.Plain, again.kind)
    }

    @Test
    fun toolbarBullet_inheritsIndentOfListAbove() {
        val list = lines("  - 子项\n下一行")
        val bullet = memoToolbarKind(list, 1, MemoLineKind.Bullet)
        assertEquals(MemoLineKind.Bullet, bullet.kind)
        assertEquals(2, bullet.indent)
        val check = memoToolbarKind(lines("- [x] 做完\n下一项"), 1, MemoLineKind.Check)
        assertEquals(MemoLineKind.Check, check.kind)
        assertEquals(false, check.checked)
    }

    @Test
    fun toolbarHeading_cyclesLevels() {
        var line = MemoLine(1, content = "标题")
        val seen = (1..4).map {
            line = memoToolbarKind(listOf(line), 0, MemoLineKind.Heading)
            if (line.kind == MemoLineKind.Heading) line.headingLevel else 0
        }
        assertEquals(listOf(1, 2, 3, 0), seen)
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
        val (fromBullet, _) = memoApplyShortcut(MemoLine(2, MemoLineKind.Bullet, "[ ] "))!!
        assertEquals(MemoLineKind.Check, fromBullet.kind)
        assertEquals(MemoLineKind.Heading, memoApplyShortcut(MemoLine(3, content = "# "))!!.first.kind)
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
