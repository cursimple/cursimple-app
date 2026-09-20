package com.x500x.cursimple.core.kernel.model

/**
 * 课表地点里的学校名剥离。
 *
 * 教务同步下来的地点常带着学校名，同一份课表里的课本来就都在同一所学校，
 * 显示出来既占地方又必然把教室号挤掉。这里找出多处共有的机构名，显示时剥掉；
 * 数据本身不动，详情、编辑与导出仍是完整地点。
 *
 * 学校名不一定在结尾——同一个教务系统里「东13-306长江大学」和
 * 「长江大学东校区东13-306」两种写法都见得到，所以按出现位置一律剥。
 */

private val ORG_KEYWORDS = listOf("大学", "学院", "学校", "中学", "小学", "职院", "研究院")

/** 学校名里关键字前面最多认这么多个字，再往前多半已经是教室名了。 */
private const val MAX_NAME_PREFIX = 6

/** 可选的校区尾巴，例如「东校区」。 */
private val CAMPUS_TAIL = Regex("^[\\u4e00-\\u9fa5]{0,4}校区")

private fun Char.isCjk(): Boolean = this in '一'..'龥'

/** 剥离时一并吃掉学校名两侧的分隔符与空白。 */
private const val TRIM_CHARS = " 　-—·,，()（）"

/**
 * 一处地点里所有「可能是学校名」的写法。
 *
 * 不去猜名字从哪个字开始——教室名与学校名之间未必有分隔符（「实验楼三层长江大学」）。
 * 这里把关键字往前 1..[MAX_NAME_PREFIX] 个字的各种截法全列出来，
 * 交给 [sharedLocationSuffix] 用「哪一种在多处地点都出现」来定夺：
 * 各处共有的那一段才是学校名，教室号本来就各不相同。
 */
private fun orgCandidatesIn(location: String): Set<String> {
    val result = mutableSetOf<String>()
    for (keyword in ORG_KEYWORDS) {
        var index = location.indexOf(keyword)
        while (index >= 0) {
            val keywordEnd = index + keyword.length
            val campus = CAMPUS_TAIL.find(location.substring(keywordEnd))?.value.orEmpty()
            val end = keywordEnd + campus.length
            // 关键字前面至少取一个字：光杆「大学」不算学校名
            for (prefixLength in 1..MAX_NAME_PREFIX) {
                val start = index - prefixLength
                if (start < 0) break
                if (!location[start].isCjk()) break
                result.add(location.substring(start, end))
                // 带校区的写法与不带的都留一份，好跟另一种写法对上
                if (campus.isNotEmpty()) result.add(location.substring(start, keywordEnd))
            }
            index = location.indexOf(keyword, index + 1)
        }
    }
    return result
}

/**
 * 多处地点共有的学校名；不足两处共有时返回空串。
 *
 * 只要求出现在两处以上，不再要求过半：一份课表就是一所学校，
 * 而地点写法不统一（有的带校区、有的不带、有的压根没写学校）时，
 * 过半这条线会因为凑不够而整份都不剥，学校名就全留在格子里了。
 */
fun sharedLocationSuffix(locations: List<String>): String {
    val nonBlank = locations.filter { it.isNotBlank() }
    if (nonBlank.size < 2) return ""
    val counts = mutableMapOf<String, Int>()
    for (location in nonBlank) {
        for (candidate in orgCandidatesIn(location)) {
            counts[candidate] = (counts[candidate] ?: 0) + 1
        }
    }
    val best = counts.filterValues { it >= 2 }
    if (best.isEmpty()) return ""
    val topCount = best.values.max()
    // 出现次数相同时取最长的：「长江大学东校区」比「长江大学」剥得更干净
    return best.filterValues { it == topCount }.keys.maxByOrNull { it.length }.orEmpty()
}

/**
 * 剥掉地点里的学校名，出现几次剥几次，位置不限。
 *
 * [suffix] 之外也剥它的「同族」写法：认定的是「长江大学东校区」时，
 * 只写「长江大学」的那些地点同样要剥干净，不然一半格子里还留着学校名。
 *
 * 剥完什么都不剩时原样返回。要「什么都不剩就当没有地点」的场景用
 * [strippedLocationOrNull]——课表格子就是这种：一份课表就一所学校，
 * 在格子里写校名等于没写。
 */
fun stripLocationSuffix(location: String, suffix: String): String {
    if (suffix.isEmpty()) return location
    // 同族的各种写法，长的先剥，否则「…东校区」会剩下一个没头没脑的「东校区」
    val targets = orgCandidatesIn(location)
        .filter { it == suffix || it.startsWith(suffix) || suffix.startsWith(it) }
        .sortedByDescending { it.length }
    if (targets.isEmpty()) return location
    var result = location
    for (target in targets) {
        result = result.replace(target, " ")
    }
    val cleaned = result
        .replace(Regex("\\s{2,}"), " ")
        .trim(*TRIM_CHARS.toCharArray())
    return cleaned.ifBlank { location }
}

/**
 * 剥掉学校名之后还剩下的地点；只剩学校名（或什么都不剩）时返回 null。
 *
 * 课表格子里写「长江大学」没有任何信息量——整份课表都是这所学校。
 * 与其占一行，不如把高度让给课名。
 */
fun strippedLocationOrNull(location: String, suffix: String): String? {
    if (location.isBlank()) return null
    val stripped = stripLocationSuffix(location, suffix)
    // 原样退回说明剥完是空的：这个地点本来就只写了个学校名
    if (suffix.isNotEmpty() && stripped == location && orgCandidatesIn(location).isNotEmpty()) {
        val withoutOrg = orgCandidatesIn(location)
            .sortedByDescending { it.length }
            .fold(location) { acc, candidate -> acc.replace(candidate, " ") }
            .replace(Regex("\\s{2,}"), " ")
            .trim(*TRIM_CHARS.toCharArray())
        if (withoutOrg.isBlank()) return null
    }
    return stripped.takeIf { it.isNotBlank() }
}
