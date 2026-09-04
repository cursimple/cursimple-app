package com.x500x.cursimple.app.update

/** GitHub Release 列表里的一条，只保留挑选所需的字段。 */
internal data class ReleaseEntry(
    val index: Int,
    val tagName: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val publishedAt: String,
)

/**
 * 从 Release 列表里挑出该更新到哪一个。
 *
 * 草稿永远跳过；未开启测试版更新时预发布也跳过。
 * 发布时间是 ISO 8601，按字符串比较即可得到先后顺序；时间缺失的排在最后，
 * 再按列表原序兜底，保证结果稳定。
 */
internal fun pickUpdateRelease(
    entries: List<ReleaseEntry>,
    includePrerelease: Boolean,
): ReleaseEntry? = entries
    .filterNot { it.draft }
    .filter { it.tagName.isNotBlank() }
    .filter { includePrerelease || !it.prerelease }
    .sortedWith(
        compareByDescending<ReleaseEntry> { it.publishedAt.isNotBlank() }
            .thenByDescending { it.publishedAt }
            .thenBy { it.index },
    )
    .firstOrNull()
