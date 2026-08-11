package com.pricelens.core.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.pricelens.core.data.local.db.dao.ObservationDao
import com.pricelens.core.data.local.db.dao.TaxonomyDao
import com.pricelens.core.data.local.db.dao.PriceCellDao
import com.pricelens.core.data.local.db.dao.PrototypeDao
import com.pricelens.core.data.local.db.dao.GtinMapDao
import com.pricelens.core.data.local.db.entity.ObservationEntity
import com.pricelens.core.data.local.db.entity.TaxonomyItemCacheEntity
import com.pricelens.core.data.local.db.entity.PriceCellCacheEntity
import com.pricelens.core.data.local.db.entity.PrototypeCacheEntity
import com.pricelens.core.data.local.db.entity.TaxonomyFtsEntity
import com.pricelens.core.data.local.db.entity.GtinMapEntity

@Database(
    entities = [
        ObservationEntity::class,
        TaxonomyItemCacheEntity::class,
        PriceCellCacheEntity::class,
        PrototypeCacheEntity::class,
        TaxonomyFtsEntity::class,
        GtinMapEntity::class
    ],
    version = 4
)
abstract class PriceLensDatabase : RoomDatabase() {
    abstract fun observationDao(): ObservationDao
    abstract fun taxonomyDao(): TaxonomyDao
    abstract fun priceCellDao(): PriceCellDao
    abstract fun prototypeDao(): PrototypeDao
    abstract fun gtinMapDao(): GtinMapDao
}
