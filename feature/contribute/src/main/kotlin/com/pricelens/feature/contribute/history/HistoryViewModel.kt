package com.pricelens.feature.contribute.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pricelens.core.data.local.db.entity.ObservationEntity
import com.pricelens.core.data.repository.ObservationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val observationRepository: ObservationRepository
) : ViewModel() {

    val history: StateFlow<List<ObservationEntity>> = observationRepository
        .getHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onRetryTapped(observationId: String) {
        viewModelScope.launch {
            observationRepository.submitObservation(observationId)
        }
    }
}
