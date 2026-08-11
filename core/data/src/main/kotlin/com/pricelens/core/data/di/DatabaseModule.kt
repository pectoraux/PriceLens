package com.pricelens.core.data.di

import android.content.Context
import androidx.room.Room
import com.pricelens.core.data.local.db.PriceLensDatabase
import com.pricelens.core.data.local.db.dao.ObservationDao
import com.pricelens.core.data.local.db.dao.PriceCellDao
import com.pricelens.core.data.local.db.dao.PrototypeDao
import com.pricelens.core.data.local.db.dao.TaxonomyDao
import com.pricelens.core.data.local.db.dao.GtinMapDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PriceLensDatabase {
        return Room.databaseBuilder(
            context,
            PriceLensDatabase::class.java,
            "pricelens.db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideObservationDao(db: PriceLensDatabase): ObservationDao = db.observationDao()

    @Provides
    fun provideTaxonomyDao(db: PriceLensDatabase): TaxonomyDao = db.taxonomyDao()

    @Provides
    fun providePriceCellDao(db: PriceLensDatabase): PriceCellDao = db.priceCellDao()

    @Provides
    fun providePrototypeDao(db: PriceLensDatabase): PrototypeDao = db.prototypeDao()

    @Provides
    fun provideGtinMapDao(db: PriceLensDatabase): GtinMapDao = db.gtinMapDao()
}
