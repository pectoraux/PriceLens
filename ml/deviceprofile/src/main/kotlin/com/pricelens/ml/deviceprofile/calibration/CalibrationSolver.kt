package com.pricelens.ml.deviceprofile.calibration

import android.graphics.Bitmap
import android.graphics.Color
import com.pricelens.core.common.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

@Singleton
class CalibrationSolver @Inject constructor(
    private val logger: Logger
) {
    data class CalibrationResult(
        val ccm: FloatArray, // 3x3 matrix
        val vignettingCoeffs: FloatArray, // [v1, v2]
        val estimatedCct: Float
    )

    /**
     * Solves for color correction and vignetting using a neutral white-sheet capture.
     */
    fun solve(bitmap: Bitmap): CalibrationResult {
        val width = bitmap.width
        val height = bitmap.height
        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = sqrt(centerX * centerX + centerY * centerY)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0
        
        // Sample center region for CCM
        val sampleSize = 50
        var sampleCount = 0
        for (y in (height/2 - sampleSize)..(height/2 + sampleSize)) {
            for (x in (width/2 - sampleSize)..(width/2 + sampleSize)) {
                val p = pixels[y * width + x]
                sumR += Color.red(p)
                sumG += Color.green(p)
                sumB += Color.blue(p)
                sampleCount++
            }
        }
        
        val avgR = sumR / sampleCount
        val avgG = sumG / sampleCount
        val avgB = sumB / sampleCount
        
        // CCM (Diagonal matrix for white balance)
        val target = (avgR + avgG + avgB) / 3.0
        val ccm = floatArrayOf(
            (target / avgR).toFloat(), 0f, 0f,
            0f, (target / avgG).toFloat(), 0f,
            0f, 0f, (target / avgB).toFloat()
        )

        // Vignetting: Radial fall-off analysis
        // Placeholder for v1, v2 estimation logic
        val vignetting = floatArrayOf(0.1f, 0.05f) 

        val cct = estimateCct(avgR.toFloat(), avgG.toFloat(), avgB.toFloat())
        
        logger.i("CalibrationSolver", "Solved: CCT=$cct, CCM diag=[${ccm[0]}, ${ccm[4]}, ${ccm[8]}]")
        
        return CalibrationResult(ccm, vignetting, cct)
    }

    private fun estimateCct(r: Float, g: Float, b: Float): Float {
        // McCamy's approximation for CCT from chromaticity
        val x = r / (r + g + b)
        val y = g / (r + g + b)
        val n = (x - 0.3320) / (0.1858 - y)
        return (449.0 * n.pow(3) + 3525.0 * n.pow(2) + 6823.3 * n + 5524.3).toFloat()
    }
}
