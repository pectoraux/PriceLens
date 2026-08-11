package com.pricelens.core.data.local.db.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = TaxonomyItemCacheEntity::class)
@Entity(tableName = "taxonomy_fts")
data class TaxonomyFtsEntity(
    val displayName: String,
    val vernacularNames: String
)
