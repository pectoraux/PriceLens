package com.pricelens.ml.portion.di

import com.pricelens.ml.portion.DefaultPortionEstimator
import com.pricelens.ml.pipeline.contract.PortionEstimator
import com.pricelens.ml.pipeline.fake.FakePortionEstimator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PortionModule {
    @Provides
    @Singleton
    fun providePortionEstimator(
        defaultPortionEstimator: DefaultPortionEstimator
    ): PortionEstimator {
        // Real estimator needs distance data. For now, let's just use it or a fake.
        // Let's stick with the real one for now as it's not LiteRT based.
        return defaultPortionEstimator
    }
}
