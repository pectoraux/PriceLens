package com.pricelens.ml.retrieval

import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.core.data.local.db.entity.*
import com.pricelens.ml.pipeline.contract.Retriever
import com.pricelens.ml.retrieval.hnsw.HnswIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class HnswRetriever @Inject constructor(
    private val taxonomyRepository: TaxonomyRepository,
    scope: CoroutineScope
) : Retriever {

    private var index: HnswIndex? = null

    init {
        scope.launch {
            taxonomyRepository.catalogUpdated.collectLatest {
                refreshIndex()
            }
        }
        refreshIndex()
    }

    private fun refreshIndex() {
        val indexFile = taxonomyRepository.getIndexFile()
        if (indexFile.exists()) {
            loadIndex(indexFile)
        }
    }

    private fun loadIndex(indexFile: File) {
        index?.close()
        index = HnswIndex(indexFile)
    }

    override suspend fun retrieve(embedding: FloatArray): List<Retriever.Candidate> = coroutineScope {
        // Parallel Stage 4: Text Similarity (4.i) and Prototype kNN (4.ii)
        
        val textSearch = async {
            performZeroShotRetrieval(embedding)
        }
        
        val knnSearch = async {
            index?.search(embedding, 32)?.map { 
                Retriever.Candidate(it.label, normalizeDistance(it.distance))
            } ?: emptyList()
        }

        val textResults = textSearch.await()
        val knnResults = knnSearch.await()
        val results = textResults + knnResults
        
        // Return top unique candidates
        results.groupBy { it.label }
            .map { (label, candidates) -> 
                Retriever.Candidate(label, candidates.maxOf { it.score })
            }
            .sortedByDescending { it.score }
            .take(32)
    }

    private suspend fun performZeroShotRetrieval(embedding: FloatArray): List<Retriever.Candidate> {
        val taxonomy: List<TaxonomyItemCacheEntity> = taxonomyRepository.allItems.first()
        return taxonomy.map { item ->
            val textEmbedding = item.textEmbedding 
            val similarity = cosineSimilarity(embedding, decodeEmbedding(textEmbedding))
            Retriever.Candidate(item.slug, similarity)
        }.sortedByDescending { it.score }.take(10)
    }

    private fun decodeEmbedding(bytes: ByteArray): FloatArray {
        // Placeholder for real float16/float32 decoding
        return FloatArray(512)
    }

    private fun normalizeDistance(dist: Float): Float {
        // Convert L2 distance to a 0-1 score (approximate)
        return (1.0f / (1.0f + dist))
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dotProduct = 0.0f
        var normA = 0.0f
        var normB = 0.0f
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        if (normA == 0f || normB == 0f) return 0f
        return dotProduct / (sqrt(normA) * sqrt(normB))
    }
}
