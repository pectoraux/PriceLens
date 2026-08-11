package com.pricelens.ml.deviceprofile.optical

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import com.pricelens.core.data.model.OpticalProfileProto
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.tan

@Singleton
class CanonicalReprojector @Inject constructor() {

    companion object {
        const val CANONICAL_WIDTH = 448
        const val CANONICAL_HEIGHT = 448
        const val CANONICAL_HFOV_DEG = 55.0f
        const val DEG_PER_PX = CANONICAL_HFOV_DEG / CANONICAL_WIDTH // 0.1228
    }

    /**
     * Projects the input bitmap into the canonical camera space using a pixel remap.
     * This ensures scale invariance and applies undistortion.
     */
    fun reproject(
        input: Bitmap,
        opticalProfile: OpticalProfileProto,
        currentZoom: Float = 1.0f
    ): Bitmap {
        val output = Bitmap.createBitmap(CANONICAL_WIDTH, CANONICAL_HEIGHT, Bitmap.Config.ARGB_8888)
        
        val distortion = opticalProfile.distortionList
        val k1 = distortion.getOrNull(0) ?: 0f
        val k2 = distortion.getOrNull(1) ?: 0f
        val k3 = distortion.getOrNull(2) ?: 0f
        val p1 = distortion.getOrNull(3) ?: 0f
        val p2 = distortion.getOrNull(4) ?: 0f

        val distModel = DistortionModel(
            k1, k2, k3, p1, p2,
            opticalProfile.getFX() * currentZoom,
            opticalProfile.getFY() * currentZoom,
            opticalProfile.getCX(),
            opticalProfile.getCY()
        )

        val canonicalFX = (CANONICAL_WIDTH / 2.0f) / tan(Math.toRadians(CANONICAL_HFOV_DEG / 2.0).toFloat())
        val centerCX = CANONICAL_WIDTH / 2.0f
        val centerCY = CANONICAL_HEIGHT / 2.0f

        val outPixels = IntArray(CANONICAL_WIDTH * CANONICAL_HEIGHT)
        val inWidth = input.width
        val inHeight = input.height
        val inPixels = IntArray(inWidth * inHeight)
        input.getPixels(inPixels, 0, inWidth, 0, 0, inWidth, inHeight)

        for (y in 0 until CANONICAL_HEIGHT) {
            for (x in 0 until CANONICAL_WIDTH) {
                // 1. Map canonical pixel (x, y) to a ray (u, v) in rectilinear space
                val u = (x - centerCX) / canonicalFX
                val v = (y - centerCY) / canonicalFX

                // 2. Project ray to device sensor coordinates (distorted)
                val deviceRectX = u * (opticalProfile.getFX() * currentZoom) + opticalProfile.getCX()
                val deviceRectY = v * (opticalProfile.getFY() * currentZoom) + opticalProfile.getCY()

                // 3. Apply Brown-Conrady distortion to find source pixel
                val (srcX, srcY) = distModel.distort(deviceRectX, deviceRectY)

                // 4. Bilinear interpolation
                outPixels[y * CANONICAL_WIDTH + x] = sampleBilinear(inPixels, inWidth, inHeight, srcX, srcY)
            }
        }

        output.setPixels(outPixels, 0, CANONICAL_WIDTH, 0, 0, CANONICAL_WIDTH, CANONICAL_HEIGHT)
        return output
    }

    private fun sampleBilinear(pixels: IntArray, width: Int, height: Int, x: Float, y: Float): Int {
        val x1 = x.toInt()
        val y1 = y.toInt()
        val x2 = x1 + 1
        val y2 = y1 + 1

        if (x1 < 0 || x2 >= width || y1 < 0 || y2 >= height) return 0xFF000000.toInt() // Black (letterbox)

        val dx = x - x1
        val dy = y - y1

        val p11 = pixels[y1 * width + x1]
        val p21 = pixels[y1 * width + x2]
        val p12 = pixels[y2 * width + x1]
        val p22 = pixels[y2 * width + x2]

        fun interpolate(c1: Int, c2: Int, c3: Int, c4: Int): Int {
            val a = (c1 and 0xFF) * (1 - dx) * (1 - dy) +
                    (c2 and 0xFF) * dx * (1 - dy) +
                    (c3 and 0xFF) * (1 - dx) * dy +
                    (c4 and 0xFF) * dx * dy
            return a.toInt() and 0xFF
        }

        val r = interpolate((p11 shr 16) and 0xFF, (p21 shr 16) and 0xFF, (p12 shr 16) and 0xFF, (p22 shr 16) and 0xFF)
        val g = interpolate((p11 shr 8) and 0xFF, (p21 shr 8) and 0xFF, (p12 shr 8) and 0xFF, (p22 shr 8) and 0xFF)
        val b = interpolate(p11 and 0xFF, p21 and 0xFF, p12 and 0xFF, p22 and 0xFF)

        return 0xFF000000.toInt() or (r shl 16) or (g shl 8) or b
    }
}
