package com.pricelens.feature.contribute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pricelens.core.data.local.db.entity.ObservationEntity
import com.pricelens.core.data.local.db.entity.TaxonomyItemCacheEntity
import com.pricelens.core.data.repository.ObservationRepository
import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.core.geo.GeoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@HiltViewModel
class ContributeViewModel @Inject constructor(
    private val observationRepository: ObservationRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val geoManager: GeoManager
) : ViewModel() {

    private val _events = MutableSharedFlow<ContributeUiEvent>()
    val events: SharedFlow<ContributeUiEvent> = _events.asSharedFlow()

    private val _uiState = MutableStateFlow<ContributeUiState>(ContributeUiState.SelectItem)
    val uiState: StateFlow<ContributeUiState> = _uiState.asStateFlow()

    private val _selectedItem = MutableStateFlow<TaxonomyItemCacheEntity?>(null)
    val selectedItem: StateFlow<TaxonomyItemCacheEntity?> = _selectedItem.asStateFlow()

    fun onItemSelected(item: TaxonomyItemCacheEntity) {
        _selectedItem.value = item
        _uiState.value = ContributeUiState.EnterDetails
    }

    fun searchTaxonomy(query: String): Flow<List<TaxonomyItemCacheEntity>> {
        return taxonomyRepository.searchItems(query)
    }

    fun submitContribution(price: Long, quantity: Double, unit: String) {
        viewModelScope.launch {
            val item = _selectedItem.value ?: return@launch
            val location = geoManager.getCurrentLocation()
            val geohash = location?.let { geoManager.getGeohash6(it) } ?: "unknown"
            
            val observation = ObservationEntity(
                id = UUID.randomUUID().toString(),
                itemId = item.slug,
                geohash = geohash,
                priceMinor = price,
                currencyCode = "KES", // TODO: Localize
                unit = unit,
                quantity = quantity,
                timestamp = System.currentTimeMillis(),
                captureSealJson = null,
                signature = null,
                syncStatus = "PENDING"
            )
            observationRepository.saveDraft(observation)
            observationRepository.submitObservation(observation.id)
            _uiState.value = ContributeUiState.Success
        }
    }

    fun onBackTapped() {
        viewModelScope.launch {
            when (_uiState.value) {
                ContributeUiState.EnterDetails -> _uiState.value = ContributeUiState.SelectItem
                else -> _events.emit(ContributeUiEvent.NavigateBack)
            }
        }
    }
}

sealed interface ContributeUiEvent {
    object NavigateBack : ContributeUiEvent
}

sealed interface ContributeUiState {
    object SelectItem : ContributeUiState
    object EnterDetails : ContributeUiState
    object Success : ContributeUiState
}
