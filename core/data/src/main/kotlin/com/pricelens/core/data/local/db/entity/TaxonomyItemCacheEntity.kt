package com.pricelens.core.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "taxonomy_item_cache")
data class TaxonomyItemCacheEntity(
    @PrimaryKey val slug: String,
    val displayName: String,
    val vernacularNames: String, // Comma-separated or JSON string
    val category: String,
    val defaultUnit: String,
    val densityKgPerL: Double?,
    val shapeModel: String?,
    val textEmbedding: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as TaxonomyItemCacheEntity

        if (slug != other.slug) return false
        if (!textEmbedding.contentEquals(other.textEmbedding)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = slug.hashCode()
        result = 31 * result + textEmbedding.contentHashCode()
        return result
    }
}
