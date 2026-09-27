package com.x500x.cursimple.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsSearchTest {

    @Test
    fun rank_titleBeforeKeywordBeforePath() {
        assertEquals(0, settingsSearchRank("背景图片", "课程与时间 › 课表背景", emptyList(), "背景"))
        assertEquals(1, settingsSearchRank("课表背景", "课程与时间", emptyList(), "背景"))
        assertEquals(2, settingsSearchRank("显示", "课程与时间", listOf("显示天数", "周一至周日"), "周日"))
        assertEquals(3, settingsSearchRank("卡片圆角", "课程与时间 › 课表样式", emptyList(), "样式"))
        // 按顺序含着这几个字也算，但排最后；一个字不认这种
        assertEquals(4, settingsSearchRank("提前几分钟", "", emptyList(), "提前分"))
        assertNull(settingsSearchRank("提前几分钟", "", emptyList(), "早"))
        assertNull(settingsSearchRank("主题", "常用", emptyList(), "  "))
        // 不分大小写、不管空格
        assertEquals(0, settingsSearchRank("WebDAV", "数据与扩展", emptyList(), "web dav"))
    }

    @Test
    fun rankList_sortsByRankThenOriginalOrderAndDropsDuplicates() {
        fun entry(title: String, path: String, keywords: List<String> = emptyList()) =
            SettingsSearchEntry(Icons.Rounded.Search, title, path, keywords) {}
        val entries = listOf(
            entry("课表样式", "课程与时间", listOf("背景颜色")),
            entry("课表背景", "课程与时间"),
            entry("背景图片", "课程与时间 › 课表背景"),
            entry("背景图片", "课程与时间 › 课表背景"),
            entry("语言", "通用"),
        )
        val titles = rankSettingsSearch(entries, "背景").map { it.title }
        assertEquals(listOf("背景图片", "课表背景", "课表样式"), titles)
    }
}
