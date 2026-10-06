package com.x500x.cursimple.core.data.term

import kotlinx.coroutines.flow.Flow

interface TermProfileRepository {
    val termsFlow: Flow<List<TermProfile>>
    val activeTermIdFlow: Flow<String>

    suspend fun activeTermId(): String

    suspend fun createTerm(name: String, termStartDateIso: String?): TermProfile
    suspend fun renameTerm(id: String, name: String)
    suspend fun setTermStartDate(id: String, dateIso: String?)

    suspend fun setTermTimingProfile(id: String, timingProfileId: String?)

    suspend fun setTermExtraWeekCount(id: String, extraWeekCount: Int)

    /**
     * Atomic read-modify-write prevents repeated UI actions from overwriting extra-week
     * increments.
     */
    suspend fun adjustActiveTermExtraWeekCount(delta: Int): Int
    suspend fun deleteTerm(id: String)
    suspend fun setActiveTerm(id: String)

    /** Seed an empty library from [legacyTermStartDateIso] and return the active ID. */
    suspend fun ensureBootstrapped(defaultName: String, legacyTermStartDateIso: String?): String
}
