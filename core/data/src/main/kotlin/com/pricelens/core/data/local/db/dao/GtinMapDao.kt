package com.pricelens.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pricelens.core.data.local.db.entity.GtinMapEntity

@Dao
interface GtinMapDao {
    @Query("SELECT itemSlug FROM gtin_map WHERE gtin = :gtin")
    suspend fun findSlugByGtin(gtin: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGtinMappings(mappings: List<GtinMapEntity>): Unit
}
