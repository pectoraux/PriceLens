package com.pricelens.ml.pipeline.di

import com.pricelens.ml.pipeline.contract.*
import com.pricelens.ml.pipeline.fake.FakeTextMatcher
import com.pricelens.ml.deviceprofile.photometric.PhotometricNormalizer
import com.pricelens.ml.deviceprofile.quality.FrameQualityGate
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PipelineModule {

    @Provides
    @Singleton
    fun provideQualityGate(gate: FrameQualityGate): QualityGate = gate

    @Provides
    @Singleton
    fun provideFrameNormalizer(normalizer: PhotometricNormalizer): FrameNormalizer = normalizer

    @Provides
    @Singleton
    fun provideTextMatcher(): TextMatcher = FakeTextMatcher()
}
