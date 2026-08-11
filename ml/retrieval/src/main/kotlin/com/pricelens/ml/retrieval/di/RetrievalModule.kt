package com.pricelens.ml.retrieval.di

import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.ml.retrieval.HnswRetriever
import com.pricelens.ml.pipeline.contract.Retriever
import com.pricelens.ml.pipeline.fake.FakeRetriever
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RetrievalModule {
    @Provides
    @Singleton
    fun provideRetriever(
        taxonomyRepository: TaxonomyRepository,
        hnswRetriever: HnswRetriever
    ): Retriever {
        // HNSW index file is in retrieval/index.hnsw
        val indexFile = taxonomyRepository.getIndexFile()
        return if (indexFile.exists()) {
            hnswRetriever
        } else {
            FakeRetriever()
        }
    }
}
