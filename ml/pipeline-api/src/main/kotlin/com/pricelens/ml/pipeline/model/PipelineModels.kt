package com.pricelens.ml.pipeline.model

import android.graphics.Bitmap

/**
 * Performance metrics for a single pipeline stage.
 */
data class StageTiming(
    val stageName: String,
    val latencyMs: Long,
    val status: StageStatus = StageStatus.SUCCESS
)

enum class StageStatus {
    SUCCESS,
    FAILURE,
    TIMEOUT,
    SKIPPED
}

/**
 * Wrapper for stage results that includes timing.
 */
data class StageResult<T>(
    val data: T?,
    val timing: StageTiming
)

/**
 * The final output of the recognition pipeline.
 */
data class PipelineResult(
    val label: String,
    val confidence: Float,
    val abstained: Boolean,
    val candidates: List<String> = emptyList(),
    val massGrams: Float? = null,
    val syntheticScore: Float = 1.0f,
    val frameHash: String? = null,
    val evidenceHash: String? = null,
    val embedding: FloatArray? = null,
    val qualityIssues: List<String> = emptyList(),
    val canonicalFrame: Bitmap,
    val redactedFrame: Bitmap? = null,
    val timings: List<StageTiming>,
    val metadata: Map<String, Any> = emptyMap(),
    val imuWindow: List<Triple<Float, Float, Float>>? = null
)
