package com.pricelens.feature.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pricelens.core.data.local.db.entity.PriceCellCacheEntity
import com.pricelens.core.data.repository.PriceRepository
import com.pricelens.core.geo.GeoManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val priceRepository: PriceRepository,
    private val geoManager: GeoManager
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val localPrices: StateFlow<List<PriceCellCacheEntity>> = flow<String> {
        val location = geoManager.getCurrentLocation()
        val geohash = location?.let { geoManager.getGeohash6(it) } ?: "unknown"
        emit(geohash)
    }.flatMapLatest { geohash ->
        priceRepository.getLocalPrices(geohash)
    }.combine(_searchQuery) { prices: List<PriceCellCacheEntity>, query: String ->
        if (query.isBlank()) prices
        else prices.filter { it.key.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }
}
