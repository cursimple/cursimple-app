package com.x500x.cursimple.app.update

/**
 * Image-bearing level-two sections become highlights; introduction and text-only sections form
 * the final summary page.
 */
data class ReleaseAnnouncement(
    val highlights: List<ReleaseHighlight>,
    val rest: List<ReleaseNoteBlock>,
)

data class ReleaseHighlight(
    val title: String,
    val body: List<ReleaseNoteBlock>,
    val image: ReleaseNoteImage,
)

/** Return null without images so callers use the ordinary scrollable view. */
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
            // Omit the document title already provided by the dialog.
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
