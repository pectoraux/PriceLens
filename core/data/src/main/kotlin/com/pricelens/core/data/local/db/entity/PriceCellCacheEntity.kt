package com.pricelens.core.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_cell_cache")
data class PriceCellCacheEntity(
    @PrimaryKey val key: String, // "$geohash6|$itemSlug|$unit"
    val p10Minor: Long,
    val p50Minor: Long,
    val p90Minor: Long,
    val currencyCode: String,
    val nObservations: Int,
    val nContributors: Int,
    val freshnessDays: Int,
    val confidence: String,
    val computedAtMs: Long
)
