package com.pricelens.ml.fusion

import com.pricelens.ml.pipeline.contract.Fuser
import com.pricelens.ml.pipeline.contract.Retriever
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp

@Singleton
class LateFuser @Inject constructor() : Fuser {

    // These weights should eventually be learned (E-09)
    private object Weights {
        const val W_ZS = 0.2f
        const val W_KNN = 0.8f
        const val W_OCR = 10.0f // Near-certainty
    }

    override suspend fun fuse(
        visualCandidates: List<Retriever.Candidate>,
        textMatches: Map<String, Float>,
        priors: Map<String, Float>
    ): List<Fuser.Prediction> {
        val logits = mutableMapOf<String, Float>()

        // 1. Visual Evidence (Text similarity + kNN)
        visualCandidates.forEach { cand ->
            // In my implementation, visualCandidates already combined ZS and kNN.
            // Let's assume the score is already weighted or we apply a generic weight.
            logits[cand.label] = (logits[cand.label] ?: 0f) + cand.score
        }

        // 2. OCR Evidence
        textMatches.forEach { (label, score) ->
            logits[label] = (logits[label] ?: 0f) + (score * Weights.W_OCR)
        }

        // 3. Log Priors (Locality, Seasonality, History)
        priors.forEach { (label, logPrior) ->
            logits[label] = (logits[label] ?: 0f) + logPrior
        }

        // 4. Softmax
        return computeSoftmax(logits)
    }

    private fun computeSoftmax(logits: Map<String, Float>, temperature: Float = 1.0f): List<Fuser.Prediction> {
        if (logits.isEmpty()) return emptyList()

        val maxLogit = logits.values.max()
        val exps = logits.mapValues { (_, v) -> exp(((v - maxLogit) / temperature).toDouble()).toFloat() }
        val sumExps = exps.values.sum()

        return exps.map { (label, p) ->
            Fuser.Prediction(label, p / sumExps)
        }.sortedByDescending { it.confidence }
    }
}
