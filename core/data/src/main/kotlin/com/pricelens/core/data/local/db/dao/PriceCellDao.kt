package com.pricelens.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pricelens.core.data.local.db.entity.PriceCellCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PriceCellDao {
    @Query("SELECT * FROM price_cell_cache WHERE `key` = :key")
    suspend fun getPriceCell(key: String): PriceCellCacheEntity?

    @Query("SELECT * FROM price_cell_cache WHERE `key` LIKE :geohashPrefix || '%'")
    fun getPriceCellsForLocality(geohashPrefix: String): Flow<List<PriceCellCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPriceCells(cells: List<PriceCellCacheEntity>): Unit
}
