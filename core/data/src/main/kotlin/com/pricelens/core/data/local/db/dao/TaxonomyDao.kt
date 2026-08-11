package com.pricelens.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.pricelens.core.data.local.db.entity.TaxonomyItemCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaxonomyDao {
    @Query("SELECT * FROM taxonomy_item_cache")
    fun getAllItems(): Flow<List<TaxonomyItemCacheEntity>>

    @Query("SELECT * FROM taxonomy_item_cache WHERE slug = :slug")
    suspend fun getItemBySlug(slug: String): TaxonomyItemCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<TaxonomyItemCacheEntity>): Unit

    @Query("DELETE FROM taxonomy_item_cache")
    suspend fun clearAll(): Unit

    @androidx.room.Transaction
    suspend fun updateTaxonomyAtomic(items: List<TaxonomyItemCacheEntity>) {
        clearAll()
        insertItems(items)
    }

    @Query("""
        SELECT * FROM taxonomy_item_cache 
        JOIN taxonomy_fts ON taxonomy_item_cache.displayName = taxonomy_fts.displayName
        WHERE taxonomy_fts MATCH :query
    """)
    fun searchItems(query: String): Flow<List<TaxonomyItemCacheEntity>>
}
