package com.pricelens.core.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gtin_map")
data class GtinMapEntity(
    @PrimaryKey val gtin: String,
    val itemSlug: String
)
