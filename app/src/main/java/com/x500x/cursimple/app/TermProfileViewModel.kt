package com.x500x.cursimple.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.data.term.TermProfile
import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.data.term.termStartDateIsoOf
import com.x500x.cursimple.core.kernel.model.TermMergeAlignment
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    private val merger: (suspend (ids: List<String>, name: String, alignment: TermMergeAlignment, anchorTermId: String?) -> String?)? = null,
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
                // Treat date clearing as an explicit user choice so plugin sync cannot restore it.
                userPrefs.setTermStartUserDecided(true)
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
            val iso = newActive?.termStartDate
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
        }
    }

    /** 合并后切到新学期，和切换学期一样同步开学日期与节次方案。 */
    fun merge(
        ids: List<String>,
        name: String,
        alignment: TermMergeAlignment = TermMergeAlignment.ByRealDate,
        anchorTermId: String? = null,
        onMerged: (String) -> Unit = {},
    ) {
        val run = merger ?: return
        viewModelScope.launch {
            val mergedId = run(ids, name, alignment, anchorTermId) ?: return@launch
            val iso = termRepo.termsFlow.first().termStartDateIsoOf(mergedId)
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
            onMerged(name)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            termRepo.deleteTerm(id)
            val activeId = termRepo.activeTermIdFlow.first()
            val iso = termRepo.termsFlow.first().termStartDateIsoOf(activeId)
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
        }
    }
}

class TermProfileViewModelFactory(
    private val termRepo: TermProfileRepository,
    private val userPrefs: UserPreferencesRepository,
    private val onActiveTermChanged: suspend () -> Unit = {},
    private val merger: (suspend (ids: List<String>, name: String, alignment: TermMergeAlignment, anchorTermId: String?) -> String?)? = null,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TermProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TermProfileViewModel(termRepo, userPrefs, onActiveTermChanged, merger) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
