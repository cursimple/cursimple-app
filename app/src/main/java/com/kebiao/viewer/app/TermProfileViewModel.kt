package com.kebiao.viewer.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kebiao.viewer.core.data.UserPreferencesRepository
import com.kebiao.viewer.core.data.term.TermProfile
import com.kebiao.viewer.core.data.term.TermProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TermProfileUiState(
    val terms: List<TermProfile> = emptyList(),
    val activeTermId: String = "",
)

class TermProfileViewModel(
    private val termRepo: TermProfileRepository,
    private val userPrefs: UserPreferencesRepository,
    private val onActiveTermChanged: suspend () -> Unit = {},
) : ViewModel() {

    val state: StateFlow<TermProfileUiState> = combine(
        termRepo.termsFlow,
        termRepo.activeTermIdFlow,
    ) { terms, activeId ->
        TermProfileUiState(terms = terms.sortedBy { it.createdAt }, activeTermId = activeId)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TermProfileUiState())

    fun createTerm(name: String, startDate: LocalDate?) {
        viewModelScope.launch {
            val term = termRepo.createTerm(name = name, termStartDateIso = startDate?.toString())
            // Newly-created term auto-becomes active so the user can immediately work in it.
            termRepo.setActiveTerm(term.id)
            userPrefs.setTermStartDate(startDate)
            onActiveTermChanged()
        }
    }

    fun renameTerm(id: String, name: String) {
        viewModelScope.launch { termRepo.renameTerm(id, name) }
    }

    fun setStartDate(id: String, date: LocalDate?) {
        viewModelScope.launch {
            termRepo.setTermStartDate(id, date?.toString())
            if (id == state.value.activeTermId) {
                userPrefs.setTermStartDate(date)
            }
        }
    }

    fun activate(id: String) {
        viewModelScope.launch {
            termRepo.setActiveTerm(id)
            val newActive = termRepo.termsFlow.let { flow ->
                flow.let {
                    state.value.terms.firstOrNull { t -> t.id == id }
                }
            }
            // Mirror the active term's start date into user prefs so the schedule UI/widgets
            // continue to read from a single source.
            val iso = newActive?.termStartDate
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            termRepo.deleteTerm(id)
            // Re-mirror new active's start date into user prefs.
            val nextActive = state.value.terms.firstOrNull { it.id != id }
            val iso = nextActive?.termStartDate
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
        }
    }
}

class TermProfileViewModelFactory(
    private val termRepo: TermProfileRepository,
    private val userPrefs: UserPreferencesRepository,
    private val onActiveTermChanged: suspend () -> Unit = {},
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TermProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TermProfileViewModel(termRepo, userPrefs, onActiveTermChanged) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
