package com.pricelens.ml.pipeline

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.pricelens.core.data.model.DeviceProfileProto
import com.pricelens.core.data.model.OpticalProfileProto
import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.domain.policy.AbstentionPolicy
import com.pricelens.domain.policy.RecognitionConfidence
import com.pricelens.ml.pipeline.contract.*
import com.pricelens.ml.pipeline.model.*
import kotlinx.coroutines.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.system.measureTimeMillis

@Singleton
class InferencePipeline @Inject constructor(
    private val qualityGate: QualityGate,
    private val frameNormalizer: FrameNormalizer,
    private val detector: Detector,
    private val embedder: Embedder,
    private val retriever: Retriever,
    private val textMatcher: TextMatcher,
    private val ocrReader: OcrReader,
    private val portionEstimator: PortionEstimator,
    private val fuser: Fuser,
    private val taxonomyRepository: TaxonomyRepository
) {
    /**
     * Latency budgets from doc 01 (Stage timings, p95).
     */
    private object Budgets {
        const val QUALITY_GATE = 8L
        const val NORMALIZATION = 25L
        const val DETECTION = 90L
        const val EMBEDDING = 110L
        const val OCR_BARCODE = 120L
        const val RETRIEVAL = 15L
        const val FUSION = 5L
        const val PORTION = 40L
    }

    suspend fun processFrame(
        rawFrame: Bitmap,
        deviceProfile: DeviceProfileProto,
        zoom: Float = 1.0f,
        distanceMm: Float = 300f,
        priors: Map<String, Float> = emptyMap(),
        imuWindow: List<Triple<Float, Float, Float>>? = null
    ): PipelineResult = coroutineScope {
        val timings = mutableListOf<StageTiming>()
        val opticalProfile = deviceProfile.optical
        val photometricProfile = if (deviceProfile.hasPhotometric()) deviceProfile.photometric else null

        // Stage 0: Quality Gate
        val qualityResult = runStage("QualityGate", Budgets.QUALITY_GATE, timings) {
            qualityGate.assess(rawFrame, imuWindow)
        } ?: QualityGate.QualityResult(false, 0f, 0f, 0f, listOf("INTERNAL_ERROR"))
        
        if (!qualityResult.isAcceptable) {
            return@coroutineScope PipelineResult(
                label = "",
                confidence = 0f,
                abstained = true,
                qualityIssues = qualityResult.issues,
                syntheticScore = qualityResult.syntheticScore,
                canonicalFrame = rawFrame, // Fallback to raw frame if normalization skipped
                timings = timings
            )
        }

        // Stage 1: Normalization
        val canonicalFrame = runStage("Normalization", Budgets.NORMALIZATION, timings) {
            frameNormalizer.normalize(rawFrame, opticalProfile, photometricProfile, zoom)
        }
        
        if (canonicalFrame == null) {
            return@coroutineScope PipelineResult(
                label = "",
                confidence = 0f,
                abstained = true,
                qualityIssues = listOf("NORMALIZATION_FAILED"),
                canonicalFrame = rawFrame,
                timings = timings
            )
        }

        // Stage 2: Detection
        var detections = runStage("Detection", Budgets.DETECTION, timings) {
            detector.detect(canonicalFrame)
        } ?: emptyList()

        // Stage 2.0: Privacy Redaction (E-04)
        val redactedBitmap = if (detections.any { it.isPerson }) {
            redactPersons(canonicalFrame.bitmap, detections)
        } else {
            null
        }
        
        // Stage 2.1: Focus Sharpness Calculation (E-05)
        detections = detections.map { detection ->
            val sharpness = calculateFocusSharpness(canonicalFrame.bitmap, detection.boundingBox)
            detection.copy(focusSharpness = sharpness)
        }

        // Stage 2.5: Dominant Region Selection (E-05)
        val dominantObject = selectDominantRegion(detections)
        
        if (dominantObject == null) {
            return@coroutineScope PipelineResult(
                label = "",
                confidence = 0f,
                abstained = true,
                qualityIssues = if (detections.isEmpty()) listOf("NO_OBJECTS_DETECTED") else listOf("NO_DOMINANT_OBJECT"),
                canonicalFrame = canonicalFrame.bitmap,
                timings = timings
            )
        }

        // Stage 3: Feature Extraction (Parallel)
        val embeddingDeferred = async {
            runStage("Embedding", Budgets.EMBEDDING, timings) {
                embedder.embed(canonicalFrame, dominantObject)
            }
        }
        val ocrResultDeferred = async {
            runStage("OcrReader", Budgets.OCR_BARCODE, timings) {
                ocrReader.read(canonicalFrame, dominantObject)
            }
        }
        val textMatchesDeferred = async {
            runStage("TextMatcher", Budgets.OCR_BARCODE, timings) { // Shared budget with OCR
                textMatcher.match(canonicalFrame, dominantObject)
            }
        }
        val portionResultDeferred = async {
            runStage("PortionEstimator", Budgets.PORTION, timings) {
                portionEstimator.estimate(canonicalFrame, dominantObject, distanceMm)
            }
        }

        val embedding = embeddingDeferred.await() ?: FloatArray(512)
        val ocrResult = ocrResultDeferred.await() ?: OcrReader.OcrResult()
        val textMatches = textMatchesDeferred.await() ?: emptyMap()
        val portionResult = portionResultDeferred.await() ?: PortionEstimator.PortionResult(null, 0f)

        // Stage 3.5: GTIN Override (E-08)
        ocrResult.barcode?.let { gtin ->
            val resolvedItem = taxonomyRepository.resolveGtin(gtin)
            if (resolvedItem != null) {
                return@coroutineScope PipelineResult(
                    label = resolvedItem.displayName,
                    confidence = 1.0f,
                    abstained = false,
                    massGrams = ocrResult.massGrams ?: portionResult.massGrams,
                    syntheticScore = qualityResult.syntheticScore,
                    frameHash = canonicalFrame.frameHash,
                    embedding = embedding,
                    qualityIssues = qualityResult.issues,
                    canonicalFrame = canonicalFrame.bitmap,
                    timings = timings,
                    metadata = mapOf("gtin" to gtin)
                )
            }
        }

        // Stage 4: Retrieval
        val visualCandidates = runStage("Retrieval", Budgets.RETRIEVAL, timings) {
            retriever.retrieve(embedding)
        } ?: emptyList()

        // Stage 5: Fusion
        val predictions = runStage("Fusion", Budgets.FUSION, timings) {
            fuser.fuse(visualCandidates, textMatches, priors)
        } ?: emptyList()

        val topPrediction = predictions.firstOrNull()
        val top2Prediction = predictions.getOrNull(1)

        if (topPrediction == null) {
            return@coroutineScope PipelineResult(
                label = "",
                confidence = 0f,
                abstained = true,
                qualityIssues = listOf("FUSION_FAILED"),
                canonicalFrame = canonicalFrame.bitmap,
                timings = timings
            )
        }

        // Stage 5.5: Abstention Policy
        val recognitionConfidence = RecognitionConfidence(
            top1Label = topPrediction.label,
            top1Probability = topPrediction.confidence,
            top2Probability = top2Prediction?.confidence ?: 0f,
            qualityFailed = !qualityResult.isAcceptable
        )
        val shouldAbstain = AbstentionPolicy.shouldAbstain(recognitionConfidence)

        PipelineResult(
            label = topPrediction.label,
            confidence = topPrediction.confidence,
            abstained = shouldAbstain,
            candidates = predictions.take(3).map { it.label },
            massGrams = ocrResult.massGrams ?: portionResult.massGrams,
            syntheticScore = qualityResult.syntheticScore,
            frameHash = canonicalFrame.frameHash,
            evidenceHash = calculateBitmapHash(redactedBitmap ?: canonicalFrame.bitmap),
            embedding = embedding,
            qualityIssues = qualityResult.issues,
            canonicalFrame = canonicalFrame.bitmap,
            redactedFrame = redactedBitmap,
            timings = timings
        )
    }

    private fun calculateBitmapHash(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val byteBuffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(byteBuffer)
        digest.update(byteBuffer.array())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun <T> runStage(
        name: String,
        timeoutMs: Long,
        timings: MutableList<StageTiming>,
        block: suspend () -> T
    ): T? {
        var result: T? = null
        var status = StageStatus.SUCCESS
        val latency = measureTimeMillis {
            try {
                result = withTimeout(timeoutMs) {
                    block()
                }
            } catch (e: TimeoutCancellationException) {
                status = StageStatus.TIMEOUT
            } catch (e: Exception) {
                status = StageStatus.FAILURE
            }
        }
        timings.add(StageTiming(name, latency, status))
        return result
    }

    /**
     * Implements area^0.5 × centrality × objectness × focus_sharpness (E-05).
     */
    private fun selectDominantRegion(detections: List<Detection>): Detection? {
        val nonPersons = detections.filter { !it.isPerson }
        if (nonPersons.isEmpty()) return null

        return nonPersons.maxByOrNull { detection ->
            val rect = detection.boundingBox
            val areaNorm = (rect.width() * rect.height()) / (CanonicalFrame.WIDTH * CanonicalFrame.HEIGHT).toFloat()
            
            val centerX = rect.centerX()
            val centerY = rect.centerY()
            val distFromCenter = ((centerX - CanonicalFrame.WIDTH / 2f).pow(2) + (centerY - CanonicalFrame.HEIGHT / 2f).pow(2)).pow(0.5f)
            val maxDist = ((CanonicalFrame.WIDTH / 2f).pow(2) + (CanonicalFrame.HEIGHT / 2f).pow(2)).pow(0.5f)
            val centrality = exp(-2.0 * (distFromCenter / maxDist).toDouble().pow(2.0)).toFloat()
            
            areaNorm.pow(0.5f) * centrality * detection.score * detection.focusSharpness
        }
    }

    /**
     * Laplacian variance algorithm for focus sharpness (E-05).
     */
    private fun calculateFocusSharpness(bitmap: Bitmap, rect: android.graphics.RectF): Float {
        val x = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val y = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val w = rect.width().toInt().coerceAtMost(bitmap.width - x)
        val h = rect.height().toInt().coerceAtMost(bitmap.height - y)
        if (w < 4 || h < 4) return 0f

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, x, y, w, h)
        
        val grayscale = FloatArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            grayscale[i] = (Color.red(p) * 0.299f + Color.green(p) * 0.587f + Color.blue(p) * 0.114f)
        }

        // Apply Laplacian operator (simplified)
        var sum = 0f
        var sumSq = 0f
        for (i in 1 until h - 1) {
            for (j in 1 until w - 1) {
                val center = grayscale[i * w + j]
                val laplacian = (grayscale[(i-1) * w + j] + grayscale[(i+1) * w + j] +
                                grayscale[i * w + (j-1)] + grayscale[i * w + (j+1)] - 4 * center)
                sum += laplacian
                sumSq += laplacian * laplacian
            }
        }
        
        val count = (w - 2) * (h - 2)
        val mean = sum / count
        return (sumSq / count) - (mean * mean) // Variance
    }

    private fun redactPersons(bitmap: Bitmap, detections: List<Detection>): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        
        detections.filter { it.isPerson }.forEach { person ->
            // In a real app we might use a proper Blur, but roadmaps allows "blurred or redacted"
            // Let's use a solid black box for maximum privacy as a first pass
            canvas.drawRect(person.boundingBox, paint)
        }
        return output
    }
}
