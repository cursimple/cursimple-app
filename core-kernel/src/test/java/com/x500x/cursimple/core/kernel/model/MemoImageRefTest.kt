package com.x500x.cursimple.core.kernel.model

import org.junit.Assert.*
import org.junit.Test

class MemoImageRefTest {
    private val id = "a".repeat(64)
    @Test fun `image labels are escaped and repeated references deduplicate`() {
        val markdown = MemoImageRef.markdown(id, "[Lecture]\nimage")
        val image = requireNotNull(MemoImageRef.parse(markdown))
        assertEquals(id, image.id)
        assertFalse(image.alt.contains('['))
        assertFalse(image.alt.contains('\n'))
        assertEquals(setOf(id), MemoImageRef.ids("$markdown\n$markdown"))
    }
    @Test fun `only image hashes are accepted not external files or paths`() {
        assertNull(MemoImageRef.parse("![photo](file:///secret)"))
        assertNull(MemoImageRef.parse("![photo](cursimple-image:../../secret)"))
        assertFalse(MemoImageRef.validId("../" + id))
        assertTrue(MemoImageRef.validId(id))
    }
}
