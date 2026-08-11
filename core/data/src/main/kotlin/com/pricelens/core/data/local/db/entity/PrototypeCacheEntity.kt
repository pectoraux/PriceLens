package com.pricelens.core.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "prototype_cache")
data class PrototypeCacheEntity(
    @PrimaryKey val id: Long,
    val itemSlug: String,
    val centroid: ByteArray,
    val nMembers: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PrototypeCacheEntity

        if (id != other.id) return false
        if (!centroid.contentEquals(other.centroid)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + centroid.contentHashCode()
        return result
    }
}
