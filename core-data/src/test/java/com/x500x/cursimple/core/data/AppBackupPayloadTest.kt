package com.x500x.cursimple.core.data

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AppBackupPayloadTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `an empty json object is not a backup`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(AppBackupPayload.serializer(), "{}")
        }
    }

    @Test
    fun `a payload without stores is rejected`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(AppBackupPayload.serializer(), """{"version":1}""")
        }
    }

    @Test
    fun `a payload without a version is rejected`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(AppBackupPayload.serializer(), """{"stores":[]}""")
        }
    }

    @Test
    fun `an unrelated json document is rejected`() {
        assertThrows(SerializationException::class.java) {
            json.decodeFromString(AppBackupPayload.serializer(), """{"message":"rate limited"}""")
        }
    }

    @Test
    fun `a payload keeps its version and stores`() {
        val payload = json.decodeFromString(
            AppBackupPayload.serializer(),
            """{"version":1,"createdAt":1750000000000,"stores":[{"store":"user_preferences","entries":[]}]}""",
        )

        assertEquals(1, payload.version)
        assertEquals(1750000000000L, payload.createdAt)
        assertEquals("user_preferences", payload.store(AppBackupStores.USER_PREFERENCES)?.storeName)
        assertNull(payload.store(AppBackupStores.SCHEDULE))
    }

    @Test
    fun `a missing creation time stays absent instead of becoming now`() {
        val payload = json.decodeFromString(
            AppBackupPayload.serializer(),
            """{"version":1,"stores":[]}""",
        )

        assertNull(payload.createdAt)
    }

    @Test
    fun `every exported store name is listed as restorable`() {
        val names = listOf(
            AppBackupStores.USER_PREFERENCES,
            AppBackupStores.SCHEDULE,
            AppBackupStores.MANUAL_COURSES,
            AppBackupStores.COURSE_NOTES,
            AppBackupStores.TERM_PROFILES,
            AppBackupStores.WIDGET_PREFERENCES,
            AppBackupStores.REMINDERS,
            AppBackupStores.PLUGIN_REGISTRY,
            AppBackupStores.PLUGIN_COMPONENTS,
        )

        assertEquals(names.size, AppBackupStores.ALL.size)
        assertTrue(AppBackupStores.ALL.containsAll(names))
    }
}
