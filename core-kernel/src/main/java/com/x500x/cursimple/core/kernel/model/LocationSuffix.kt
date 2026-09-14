package com.x500x.cursimple.core.kernel.model

/**
 * 课表地点里的学校名后缀剥离。
 *
 * 教务同步下来的地点常带着学校名（“东13-306长江大学”），同一门课表里的课都在同一所学校，
 * 显示出来既占地方又必然被格子截断。这里从全部地点里找出过半数共有的机构名后缀，
 * 显示时剥掉；数据本身不改动，编辑与导出仍见全名。
 */

private val ORG_KEYWORDS = listOf("大学", "学院", "学校", "中学", "小学", "职院", "研究院")

private val CAMPUS_TAIL = Regex("^[\\u4e00-\\u9fa5]{0,4}校区$")

private fun Char.isCjk(): Boolean = this in '\u4e00'..'\u9fa5'

/** 地点末尾的机构名；从关键字向前扩展整段中文，名字起点由非中文字符（数字/字母/分隔符）界定。 */
private fun orgSuffixOf(location: String): String? {
    val text = location.trim().trimEnd(' ', '　', '）', ')')
    var best: String? = null
    for (keyword in ORG_KEYWORDS) {
        var index = text.lastIndexOf(keyword)
        while (index >= 0) {
            var end = index + keyword.length
            CAMPUS_TAIL.find(text.substring(end))?.let { end += it.value.length }
            if (end == text.length) {
                var start = index
                while (start > 0 && text[start - 1].isCjk()) start--
                val candidate = text.substring(start, end)
                // 关键字前面至少要有名字本身（“大学”单独出现不算学校名）；
                // 中文连写的房号会让候选带着各自不同的楼号，多数表决时自然凑不够半数
                if (candidate.length > keyword.length &&
                    (best == null || candidate.length < best.length)
                ) {
                    best = candidate
                }
                break
            }
            index = text.lastIndexOf(keyword, index - 1)
        }
    }
    return best
}

/** 全部地点里过半数共有的机构名后缀；不足两处共有或仅仅是平票时返回空串。 */
fun sharedLocationSuffix(locations: List<String>): String {
    val candidates = locations
        .filter { it.isNotBlank() }
        .mapNotNull(::orgSuffixOf)
    if (candidates.size < 2) return ""
    val majority = candidates.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return ""
    // 严格过半才算共有：平票（各一半）时不剥，避免误伤一半课程的地点
    return if (majority.value >= 2 && majority.value * 2 > candidates.size) majority.key else ""
}

/** 剥掉地点末尾的 [suffix] 及其前面的分隔符；剥完不剩内容时原样返回。 */
fun stripLocationSuffix(location: String, suffix: String): String {
    if (suffix.isEmpty() || !location.endsWith(suffix)) return location
    val stripped = location
        .dropLast(suffix.length)
        .trimEnd(' ', '　', '-', '—', '·', ',', '，', '(', '（')
    return stripped.ifBlank { location }
}
