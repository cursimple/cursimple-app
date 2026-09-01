package com.x500x.cursimple.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.x500x.cursimple.core.data.UserPreferencesRepository
import com.x500x.cursimple.core.data.term.TermProfile
import com.x500x.cursimple.core.data.term.TermProfileRepository
import com.x500x.cursimple.core.data.term.termStartDateIsoOf
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
            // 新建的学期直接切换为活动学期。
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
            // 把活动学期的开学日期镜像到用户偏好，课表界面与小组件统一从这里读取。
            val iso = newActive?.termStartDate
            userPrefs.setTermStartDate(iso?.let { runCatching { LocalDate.parse(it) }.getOrNull() })
            onActiveTermChanged()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            termRepo.deleteTerm(id)
            // 删除后以仓库里实际的活动学期为准镜像开学日期，删除非活动学期不会改动当前学期。
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
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TermProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TermProfileViewModel(termRepo, userPrefs, onActiveTermChanged) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
