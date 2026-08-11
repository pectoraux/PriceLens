package com.pricelens.ml.fusion.di

import com.pricelens.ml.fusion.LateFuser
import com.pricelens.ml.pipeline.contract.Fuser
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FusionModule {
    @Binds
    @Singleton
    abstract fun bindFuser(lateFuser: LateFuser): Fuser
}
