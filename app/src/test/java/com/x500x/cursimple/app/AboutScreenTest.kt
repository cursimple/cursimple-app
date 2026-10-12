package com.x500x.cursimple.app

import com.x500x.cursimple.R
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutScreenTest {
    @Test fun `numeric testing versions use the testing resource`() {
        assertEquals(R.string.about_channel_beta, releaseChannelLabel("0.7.9", "beta"))
        assertEquals(R.string.about_channel_stable, releaseChannelLabel("0.7.9", "stable"))
    }

    @Test fun `about labels prioritize actual channel and support legacy versions`() {
        assertEquals(R.string.about_channel_stable, releaseChannelLabel("0.7.9-beta.1", "stable"))
        assertEquals(R.string.about_channel_beta, releaseChannelLabel("0.7.9-beta.1", null))
    }
}
