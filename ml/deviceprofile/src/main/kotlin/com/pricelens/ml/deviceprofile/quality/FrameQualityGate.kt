package com.pricelens.ml.deviceprofile.quality

import android.graphics.Bitmap
import android.graphics.Color
import com.pricelens.core.trust.TremorDetector
import com.pricelens.domain.policy.Thresholds
import com.pricelens.ml.pipeline.contract.QualityGate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

@Singleton
class FrameQualityGate @Inject constructor(
    private val tremorDetector: TremorDetector
) : QualityGate {

    override suspend fun assess(
        bitmap: Bitmap,
        imuWindow: List<Triple<Float, Float, Float>>?
    ): QualityGate.QualityResult {
        val blurScore = calculateBlurScore(bitmap)
        val exposureScore = calculateExposureScore(bitmap)
        
        // Anti-rephotography signals (H-03)
        val moireScore = detectMoire(bitmap)
        val pwmScore = detectPwmBanding(bitmap)
        val tremorScore = imuWindow?.let { tremorDetector.analyzeTremor(it) } ?: 0.5f

        val issues = mutableListOf<String>()
        if (blurScore < 10.0f) issues.add("TOO_BLURRY")
        if (exposureScore < 0.2f) issues.add("UNDER_EXPOSED")
        if (exposureScore > 0.8f) issues.add("OVER_EXPOSED")
        
        // Combined synthetic score (Stage 0)
        val syntheticScore = (moireScore + pwmScore + tremorScore) / 3.0f
        if (syntheticScore < 0.3f) issues.add("SUSPICIOUS_PROVENANCE")

        return QualityGate.QualityResult(
            isAcceptable = issues.isEmpty() || (issues.size == 1 && issues[0] == "SUSPICIOUS_PROVENANCE"),
            blurScore = blurScore,
            exposureScore = exposureScore,
            syntheticScore = syntheticScore,
            issues = issues
        )
    }

    private fun calculateBlurScore(bitmap: Bitmap): Float {
        return 15.0f // Placeholder
    }

    private fun calculateExposureScore(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        var totalLuminance = 0.0
        for (pixel in pixels) {
            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)
            totalLuminance += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
        }
        
        return (totalLuminance / pixels.size).toFloat()
    }

    private fun detectMoire(bitmap: Bitmap): Float {
        return 1.0f // Placeholder
    }

    private fun detectPwmBanding(bitmap: Bitmap): Float {
        val width = bitmap.width
        val height = bitmap.height
        val step = height / 20
        if (step == 0) return 1.0f
        
        val rowMeans = mutableListOf<Float>()
        for (y in 0 until height step step) {
            var rowSum = 0L
            for (x in 0 until width step (width / 10)) {
                val p = bitmap.getPixel(x, y)
                rowSum += (Color.red(p) + Color.green(p) + Color.blue(p)) / 3
            }
            rowMeans.add(rowSum.toFloat() / 10f)
        }
        
        val avg = rowMeans.average().toFloat()
        val variance = rowMeans.map { abs(it - avg) }.average().toFloat() / 255f
        
        return if (variance > Thresholds.PWM_VARIANCE_THRESHOLD) 0.4f else 1.0f
    }
}
