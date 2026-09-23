package com.x500x.cursimple.app.update

/**
 * 翻页式更新公告：前面几页各讲一个亮点（大标题 + 说明 + 一张图），最后一页用纯文字列出其余改动。
 *
 * 公告仍是发布说明那一份 Markdown，按约定拆页：带图的 `##` 小节是一页亮点，
 * 不带图的小节（修复、其他）连同开头的引言都归到最后一页。GitHub 网页上照常从上往下读。
 */
data class ReleaseAnnouncement(
    val highlights: List<ReleaseHighlight>,
    /** 最后一页的内容；没有剩下的内容时为空，就不出这一页。 */
    val rest: List<ReleaseNoteBlock>,
)

/** 一页亮点。[body] 是小节里的文字部分，图片单独放在 [image]。 */
data class ReleaseHighlight(
    val title: String,
    val body: List<ReleaseNoteBlock>,
    val image: ReleaseNoteImage,
)

/**
 * 把发布说明拆成翻页公告；一张图都没有时返回 null，调用方照旧整篇滚动显示——
 * 旧版本的说明没有图，拆出来只剩一页纯文字，不如原样。
 */
fun buildReleaseAnnouncement(markdown: String): ReleaseAnnouncement? {
    val blocks = parseReleaseNotes(markdown)
    if (blocks.none { it is ReleaseNoteBlock.Gallery }) return null

    val highlights = mutableListOf<ReleaseHighlight>()
    val rest = mutableListOf<ReleaseNoteBlock>()
    var heading: ReleaseNoteBlock.Heading? = null
    val section = mutableListOf<ReleaseNoteBlock>()

    fun closeSection() {
        val image = section.filterIsInstance<ReleaseNoteBlock.Gallery>().flatMap { it.images }.firstOrNull()
        val title = heading
        if (title != null && image != null) {
            highlights += ReleaseHighlight(
                title = title.spans.joinToString("") { it.text }.trim(),
                body = section.filterNot { it is ReleaseNoteBlock.Gallery },
                image = image,
            )
        } else {
            title?.let { rest += it }
            rest += section.filterNot { it is ReleaseNoteBlock.Gallery }
        }
        heading = null
        section.clear()
    }

    for (block in blocks) {
        when {
            // 一级标题是整篇的题目（「v0.7.3 更新内容」），弹窗标题已经写了版本，这里不再重复
            block is ReleaseNoteBlock.Heading && block.level == 1 -> Unit
            block is ReleaseNoteBlock.Heading && block.level == 2 -> {
                closeSection()
                heading = block
            }
            else -> section += block
        }
    }
    closeSection()

    return ReleaseAnnouncement(
        highlights = highlights,
        rest = rest.dropWhile { it == ReleaseNoteBlock.Divider }.dropLastWhile { it == ReleaseNoteBlock.Divider },
    )
}
