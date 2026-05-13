package com.x500x.cursimple.core.data.term

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A single user-managed academic term. The term's [termStartDate] is the first-week Monday
 * (or any day inside the first week) used to compute current-week-index. Each term owns its
 * own schedule snapshot and manual-course list, keyed by [id].
 */
@Serializable
data class TermProfile(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String,
    /** ISO yyyy-MM-dd; null means "ask the user". */
    @SerialName("termStartDate") val termStartDate: String? = null,
    @SerialName("createdAt") val createdAt: Long = System.currentTimeMillis(),
)
