package com.x500x.cursimple.app.update

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdatePollStateTest {

    private val minute = 60 * 1000L

    @Test
    fun `the very first round runs a full check`() {
        // 还没有 ETag 可比，条件请求只会白跑一趟
        assertEquals(
            UpdatePollAction.FullCheck,
            updatePollAction(
                nowMillis = 0L,
                lastPeekAtMillis = 0L,
                lastFullCheckAtMillis = 0L,
                hasEtag = false,
            ),
        )
    }

    @Test
    fun `a peek fires once the interval has passed`() {
        assertEquals(
            UpdatePollAction.Peek,
            updatePollAction(
                nowMillis = 20 * minute,
                lastPeekAtMillis = 0L,
                lastFullCheckAtMillis = 1L,
                hasEtag = true,
            ),
        )
    }

    @Test
    fun `nothing happens inside the interval`() {
        assertEquals(
            UpdatePollAction.Skip,
            updatePollAction(
                nowMillis = 5 * minute,
                lastPeekAtMillis = 0L,
                lastFullCheckAtMillis = 1L,
                hasEtag = true,
            ),
        )
    }

    @Test
    fun `without an etag it waits for the full-check cooldown instead of peeking`() {
        // 源站不通时拿不到 ETag，这时只能靠走镜像的完整检查兜底，但间隔要放宽
        assertEquals(
            UpdatePollAction.Skip,
            updatePollAction(
                nowMillis = 30 * minute,
                lastPeekAtMillis = 0L,
                lastFullCheckAtMillis = 1L,
                hasEtag = false,
            ),
        )
        assertEquals(
            UpdatePollAction.FullCheck,
            updatePollAction(
                nowMillis = 7 * 60 * minute,
                lastPeekAtMillis = 0L,
                lastFullCheckAtMillis = 1L,
                hasEtag = false,
            ),
        )
    }

    @Test
    fun `a fresh peek does not trigger another one`() {
        assertEquals(
            UpdatePollAction.Skip,
            updatePollAction(
                nowMillis = 100 * minute,
                lastPeekAtMillis = 99 * minute,
                lastFullCheckAtMillis = 1L,
                hasEtag = true,
            ),
        )
    }
}
