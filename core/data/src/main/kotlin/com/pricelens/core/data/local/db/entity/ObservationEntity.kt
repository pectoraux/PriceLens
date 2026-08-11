package com.pricelens.core.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "observations")
data class ObservationEntity(
    @PrimaryKey val id: String,
    val itemId: String?,
    val geohash: String,
    val priceMinor: Long,
    val currencyCode: String,
    val unit: String,
    val quantity: Double,
    val timestamp: Long,
    val captureSealJson: String?,
    val signature: ByteArray?,
    val syncStatus: String, // DRAFT, PENDING, SYNCED, REJECTED
    val predictedItemId: String? = null,
    val predictedConfidence: Float? = null,
    val labelWasCorrected: Boolean = false,
    val priceWasCorrected: Boolean = false,
    val abstained: Boolean = false,
    val embedding: ByteArray? = null,
    val lastError: String? = null,
    val consensusStatus: String = "PENDING"
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ObservationEntity

        if (id != other.id) return false
        if (signature != null) {
            if (other.signature == null) return false
            if (!signature.contentEquals(other.signature)) return false
        } else if (other.signature != null) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + (signature?.contentHashCode() ?: 0)
        return result
    }
}
