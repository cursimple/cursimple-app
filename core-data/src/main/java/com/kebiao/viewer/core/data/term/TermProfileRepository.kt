package com.kebiao.viewer.core.data.term

import kotlinx.coroutines.flow.Flow

interface TermProfileRepository {
    val termsFlow: Flow<List<TermProfile>>
    val activeTermIdFlow: Flow<String>

    /** Suspends until the active term is loaded. */
    suspend fun activeTermId(): String

    suspend fun createTerm(name: String, termStartDateIso: String?): TermProfile
    suspend fun renameTerm(id: String, name: String)
    suspend fun setTermStartDate(id: String, dateIso: String?)
    suspend fun deleteTerm(id: String)
    suspend fun setActiveTerm(id: String)

    /**
     * Bootstrap migration: if no term exists, create one named [defaultName] using
     * [legacyTermStartDateIso] (taken from old global prefs) and mark it active.
     * Returns the resulting active term id.
     */
    suspend fun ensureBootstrapped(defaultName: String, legacyTermStartDateIso: String?): String
}
