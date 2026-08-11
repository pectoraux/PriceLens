package com.pricelens.feature.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.pricelens.core.data.local.db.entity.ObservationEntity
import com.pricelens.core.data.local.db.entity.TaxonomyItemCacheEntity
import com.pricelens.core.data.repository.ObservationRepository
import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.feature.review.navigation.ReviewRoute
import com.pricelens.domain.policy.Thresholds
import com.pricelens.core.data.repository.PriceRepository
import com.pricelens.core.data.remote.model.PriceBand
import com.pricelens.core.geo.GeoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ReviewUiEvent {
    data object NavigateBack : ReviewUiEvent
    data object NavigateToHistory : ReviewUiEvent
}

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observationRepository: ObservationRepository,
    private val taxonomyRepository: TaxonomyRepository,
    private val priceRepository: PriceRepository,
    private val geoManager: GeoManager
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReviewRoute>()
    val observationId = route.observationId

    private val _isPickingItem = MutableStateFlow(false)
    val isPickingItem: StateFlow<Boolean> = _isPickingItem.asStateFlow()

    private val _quantity = MutableStateFlow("")
    val quantity: StateFlow<String> = _quantity.asStateFlow()

    private val _unit = MutableStateFlow("")
    val unit: StateFlow<String> = _unit.asStateFlow()

    private val _itemSlug = MutableStateFlow<String?>(null)

    private val _events = MutableSharedFlow<ReviewUiEvent>()
    val events: SharedFlow<ReviewUiEvent> = _events.asSharedFlow()

    val observation: StateFlow<ObservationEntity?> = observationRepository
        .observeObservation(observationId)
        .onEach { obs ->
            if (obs != null && _quantity.value.isEmpty()) {
                _quantity.value = obs.quantity.toString()
                _unit.value = obs.unit
                _itemSlug.value = obs.itemId ?: obs.predictedItemId
                
                if (obs.itemId == "item.unknown") {
                    _isPickingItem.value = true
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val priceBand: StateFlow<PriceBand?> = combine(_itemSlug, _unit, _quantity) { slug, u, q ->
        Triple(slug, u, q)
    }.flatMapLatest { (slug, u, q) ->
        if (slug == null || u.isEmpty()) {
            flowOf(null)
        } else {
            flow<PriceBand?> {
                val location = geoManager.getCurrentLocation()
                val geohash = location?.let { geoManager.getGeohash6(it) } ?: "unknown"
                emit(priceRepository.getPriceEstimate(geohash, slug, u, q.toDoubleOrNull() ?: 1.0))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isPlausible: StateFlow<Boolean> = combine(_quantity, _unit) { q, u ->
        val amount = q.toDoubleOrNull() ?: 0.0
        when (u.lowercase()) {
            "kg", "l" -> amount <= Thresholds.MAX_PLAUSIBLE_KG
            "piece" -> amount <= Thresholds.MAX_PLAUSIBLE_PIECES
            else -> true
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun onConfirmTapped(observationId: String) {
        viewModelScope.launch {
            val q = _quantity.value.toDoubleOrNull() ?: 1.0
            observationRepository.updateQuantityAndUnit(observationId, q, _unit.value)
            observationRepository.submitObservation(observationId)
            _events.emit(ReviewUiEvent.NavigateToHistory)
        }
    }

    fun onQuantityChanged(newQuantity: String) {
        _quantity.value = newQuantity
    }

    fun onUnitChanged(newUnit: String) {
        _unit.value = newUnit
    }

    fun onIncorrectTapped() {
        _isPickingItem.value = true
    }

    fun onItemSelected(observationId: String, item: TaxonomyItemCacheEntity) {
        _isPickingItem.value = false
        viewModelScope.launch {
            observationRepository.updateItem(observationId, item.slug)
        }
    }

    fun searchTaxonomy(query: String): Flow<List<TaxonomyItemCacheEntity>> {
        return taxonomyRepository.searchItems(query)
    }
}
