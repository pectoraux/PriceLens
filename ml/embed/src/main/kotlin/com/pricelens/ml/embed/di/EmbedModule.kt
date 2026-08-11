package com.pricelens.ml.embed.di

import com.pricelens.core.data.repository.ModelRepository
import com.pricelens.ml.embed.interpreter.LiteRtEmbedder
import com.pricelens.ml.pipeline.contract.Embedder
import com.pricelens.ml.pipeline.fake.FakeEmbedder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object EmbedModule {
    @Provides
    @Singleton
    fun provideEmbedder(
        modelRepository: ModelRepository,
        liteRtEmbedder: LiteRtEmbedder
    ): Embedder {
        return if (modelRepository.isModelDownloaded("embedder")) {
            liteRtEmbedder
        } else {
            FakeEmbedder()
        }
    }
}
