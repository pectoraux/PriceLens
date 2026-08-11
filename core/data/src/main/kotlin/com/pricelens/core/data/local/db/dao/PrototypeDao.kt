package com.pricelens.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pricelens.core.data.local.db.entity.PrototypeCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PrototypeDao {
    @Query("SELECT * FROM prototype_cache")
    fun getAllPrototypes(): Flow<List<PrototypeCacheEntity>>

    @Query("SELECT * FROM prototype_cache WHERE itemSlug = :itemSlug")
    fun getPrototypesForItem(itemSlug: String): Flow<List<PrototypeCacheEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrototypes(prototypes: List<PrototypeCacheEntity>)

    @Query("DELETE FROM prototype_cache")
    suspend fun clearAll()

    @androidx.room.Transaction
    suspend fun updatePrototypesAtomic(prototypes: List<PrototypeCacheEntity>) {
        clearAll()
        insertPrototypes(prototypes)
    }
}
