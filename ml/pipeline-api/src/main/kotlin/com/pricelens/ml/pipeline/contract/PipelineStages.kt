package com.pricelens.ml.pipeline.contract

import android.graphics.Bitmap
import com.pricelens.core.data.model.OpticalProfileProto
import com.pricelens.core.data.model.PhotometricProfileProto
import com.pricelens.ml.pipeline.model.CanonicalFrame
import com.pricelens.ml.pipeline.model.Detection

interface QualityGate {
    data class QualityResult(
        val isAcceptable: Boolean,
        val blurScore: Float,
        val exposureScore: Float,
        val syntheticScore: Float,
        val issues: List<String>
    )
    suspend fun assess(
        bitmap: Bitmap,
        imuWindow: List<Triple<Float, Float, Float>>? = null
    ): QualityResult
}

interface FrameNormalizer {
    suspend fun normalize(
        bitmap: Bitmap,
        opticalProfile: OpticalProfileProto,
        photometricProfile: PhotometricProfileProto?,
        zoom: Float
    ): CanonicalFrame
}

interface Detector {
    suspend fun detect(frame: CanonicalFrame): List<Detection>
}

interface Embedder {
    suspend fun embed(frame: CanonicalFrame, detection: Detection): FloatArray
}

interface Retriever {
    data class Candidate(val label: String, val score: Float)
    suspend fun retrieve(embedding: FloatArray): List<Candidate>
}

interface TextMatcher {
    suspend fun match(frame: CanonicalFrame, detection: Detection): Map<String, Float>
}

interface OcrReader {
    data class OcrResult(
        val barcode: String? = null,
        val text: String? = null,
        val massGrams: Float? = null,
        val priceMinor: Long? = null,
        val currencyCode: String? = null
    )
    suspend fun read(frame: CanonicalFrame, detection: Detection): OcrResult
}

interface PortionEstimator {
    data class PortionResult(val massGrams: Float?, val confidence: Float)
    suspend fun estimate(frame: CanonicalFrame, detection: Detection, distanceMm: Float): PortionResult
}

interface Fuser {
    data class Prediction(val label: String, val confidence: Float)
    suspend fun fuse(
        visualCandidates: List<Retriever.Candidate>,
        textMatches: Map<String, Float>,
        priors: Map<String, Float>
    ): List<Prediction>
}
