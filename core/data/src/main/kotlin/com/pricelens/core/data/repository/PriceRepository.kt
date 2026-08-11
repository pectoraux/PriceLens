package com.pricelens.core.data.repository

import com.pricelens.core.data.local.db.dao.PriceCellDao
import com.pricelens.core.data.local.db.entity.PriceCellCacheEntity
import com.pricelens.core.data.remote.api.PriceApi
import com.pricelens.core.data.remote.model.PriceBand
import com.pricelens.core.data.remote.model.PriceEstimateRequest
import io.ktor.client.call.body
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PriceRepository @Inject constructor(
    private val priceCellDao: PriceCellDao,
    private val priceApi: PriceApi
) {
    private companion object {
        const val CACHE_EXPIRY_MS = 24 * 60 * 60 * 1000L // 24 hours
    }

    fun getLocalPrices(geohash: String): Flow<List<PriceCellCacheEntity>> {
        return priceCellDao.getPriceCellsForLocality(geohash)
    }

    suspend fun getPriceEstimate(
        geohash: String,
        itemSlug: String,
        unit: String,
        quantity: Double
    ): PriceBand? {
        val key = "$geohash|$itemSlug|$unit"
        val cached = priceCellDao.getPriceCell(key)

        if (cached != null && (System.currentTimeMillis() - cached.computedAtMs) < CACHE_EXPIRY_MS) {
            return PriceBand(
                p10Minor = cached.p10Minor,
                p50Minor = cached.p50Minor,
                p90Minor = cached.p90Minor,
                currencyCode = cached.currencyCode,
                unit = unit,
                nObservations = cached.nObservations,
                nContributors = cached.nContributors,
                confidence = PriceBand.Confidence.valueOf(cached.confidence),
                freshnessDays = cached.freshnessDays
            )
        }

        // Cache miss or stale -> fetch from API
        return try {
            val request = PriceEstimateRequest(
                itemSlug = itemSlug,
                unit = unit,
                quantity = quantity,
                geohash6 = geohash,
                currencyCode = "KES", // TODO: Get from locality
                observedAt = null
            )
            val response = priceApi.estimatePrice(request)
            val band = response.body()
            
            // Update cache
            priceCellDao.insertPriceCells(listOf(
                PriceCellCacheEntity(
                    key = key,
                    p10Minor = band.p10Minor,
                    p50Minor = band.p50Minor,
                    p90Minor = band.p90Minor,
                    currencyCode = band.currencyCode,
                    nObservations = band.nObservations,
                    nContributors = band.nContributors,
                    freshnessDays = band.freshnessDays ?: 0,
                    confidence = band.confidence.value,
                    computedAtMs = System.currentTimeMillis()
                )
            ))
            band
        } catch (e: Exception) {
            null
        }
    }

    suspend fun syncPriceCells(geohash: String, itemSlugs: List<String>) {
        try {
            val response = priceApi.getPriceCells(geohash, itemSlugs, null)
            val cells = response.body()
            
            val entities = cells.map { cell ->
                PriceCellCacheEntity(
                    key = "$geohash|${cell.itemId}|${cell.unit}",
                    p10Minor = cell.p10Minor,
                    p50Minor = cell.p50Minor,
                    p90Minor = cell.p90Minor,
                    currencyCode = cell.currencyCode,
                    nObservations = 0, // Fallback
                    nContributors = 0,
                    freshnessDays = 0,
                    confidence = "MEDIUM",
                    computedAtMs = System.currentTimeMillis()
                )
            }
            priceCellDao.insertPriceCells(entities)
        } catch (e: Exception) {
            // Handle error
        }
    }
}
