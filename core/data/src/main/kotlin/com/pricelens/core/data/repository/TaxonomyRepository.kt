package com.pricelens.core.data.repository

import android.content.Context
import android.util.Base64
import com.pricelens.core.data.local.db.dao.GtinMapDao
import com.pricelens.core.data.local.db.dao.PrototypeDao
import com.pricelens.core.data.local.db.dao.TaxonomyDao
import com.pricelens.core.data.local.db.entity.PrototypeCacheEntity
import com.pricelens.core.data.local.db.entity.TaxonomyItemCacheEntity
import com.pricelens.core.data.model.CatalogBundle
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaxonomyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val taxonomyDao: TaxonomyDao,
    private val prototypeDao: PrototypeDao,
    private val gtinMapDao: GtinMapDao,
    scope: CoroutineScope
) {
    val allItems: Flow<List<TaxonomyItemCacheEntity>> = taxonomyDao.getAllItems()

    init {
        scope.launch {
            // Seed basic items for development if empty
            if (taxonomyDao.getItemBySlug("bread") == null) {
                val seedItems = listOf(
                    TaxonomyItemCacheEntity("bread", "Bread", "", "grain", "piece", 0.5, "packaged", ByteArray(1024)),
                    TaxonomyItemCacheEntity("tomato", "Tomato", "", "produce", "kg", 1.0, "spheroid", ByteArray(1024)),
                    TaxonomyItemCacheEntity("apple", "Apple", "", "produce", "kg", 1.0, "spheroid", ByteArray(1024))
                )
                taxonomyDao.insertItems(seedItems)
            }
        }
    }

    private val _catalogUpdated = MutableSharedFlow<Unit>(replay = 0)
    val catalogUpdated: SharedFlow<Unit> = _catalogUpdated.asSharedFlow()

    fun searchItems(query: String): Flow<List<TaxonomyItemCacheEntity>> {
        val ftsQuery = if (query.isBlank()) "" else "*$query*"
        return taxonomyDao.searchItems(ftsQuery)
    }

    suspend fun resolveGtin(gtin: String): TaxonomyItemCacheEntity? {
        val slug = gtinMapDao.findSlugByGtin(gtin)
        return if (slug != null) {
            taxonomyDao.getItemBySlug(slug)
        } else {
            // TODO: Server-side fallback lookup
            null
        }
    }

    suspend fun updateCatalogAtomic(bundle: CatalogBundle) {
        val taxonomyEntities = bundle.taxonomy.map { item ->
            TaxonomyItemCacheEntity(
                slug = item.slug,
                displayName = item.displayName,
                vernacularNames = item.vernacularNames ?: "",
                category = item.category,
                defaultUnit = item.defaultUnit,
                densityKgPerL = item.densityKgPerL,
                shapeModel = item.shapeModel,
                textEmbedding = Base64.decode(item.textEmbedding, Base64.DEFAULT)
            )
        }
        
        val prototypeEntities = bundle.prototypes.map { proto ->
            PrototypeCacheEntity(
                id = proto.id,
                itemSlug = proto.itemSlug,
                centroid = Base64.decode(proto.centroid, Base64.DEFAULT),
                nMembers = proto.nMembers
            )
        }

        // Apply both in separate transactions or one big if we had a combined DAO
        // For simplicity and F-06 compliance, let's use the individual DAOs' transactions
        taxonomyDao.updateTaxonomyAtomic(taxonomyEntities)
        prototypeDao.updatePrototypesAtomic(prototypeEntities)
        
        _catalogUpdated.emit(Unit)
    }

    suspend fun notifyIndexUpdated() {
        _catalogUpdated.emit(Unit)
    }

    suspend fun getItemBySlug(slug: String): TaxonomyItemCacheEntity? {
        return taxonomyDao.getItemBySlug(slug)
    }

    fun getIndexFile(): java.io.File {
        return java.io.File(context.filesDir, "retrieval/index.hnsw")
    }

    /**
     * Resolves the display name for an item based on the provided locale.
     * Fallback to the default displayName if no vernacular name is found for the locale.
     */
    fun resolveDisplayName(item: TaxonomyItemCacheEntity, locale: String): String {
        val parts = item.vernacularNames.split(",")
        val localizedName = parts.find { it.startsWith("$locale:") }?.substringAfter(":")
        return localizedName ?: item.displayName
    }
}
