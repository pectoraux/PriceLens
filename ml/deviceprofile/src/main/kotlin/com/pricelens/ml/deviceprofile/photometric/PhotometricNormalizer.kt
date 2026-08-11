package com.pricelens.ml.deviceprofile.photometric

import android.graphics.Bitmap
import android.graphics.Color
import com.pricelens.core.data.model.ColorMatrixProto
import com.pricelens.core.data.model.OpticalProfileProto
import com.pricelens.core.data.model.PhotometricProfileProto
import com.pricelens.ml.deviceprofile.optical.CanonicalReprojector
import com.pricelens.ml.pipeline.contract.FrameNormalizer
import com.pricelens.ml.pipeline.model.CanonicalFrame
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

@Singleton
class PhotometricNormalizer @Inject constructor(
    private val reprojector: CanonicalReprojector
) : FrameNormalizer {

    /**
     * Normalizes the photometric and geometric properties of the image.
     */
    override suspend fun normalize(
        bitmap: Bitmap,
        opticalProfile: OpticalProfileProto,
        photometricProfile: PhotometricProfileProto?,
        zoom: Float
    ): CanonicalFrame {
        // 1. De-gamma to linear
        // 2. Photometric normalization (CCM + D65)
        val normalized = applyPhotometricNormalization(bitmap, photometricProfile)
        
        // 3. Geometric Normalization (Reprojection)
        val canonicalBitmap = reprojector.reproject(normalized, opticalProfile, zoom)
        
        // 4. Final Frame Hashing (H-04)
        val frameHash = calculateHash(canonicalBitmap)
        
        return CanonicalFrame(
            bitmap = canonicalBitmap,
            sensorTimestampNs = 0L, // Placeholder
            frameHash = frameHash
        )
    }

    private fun calculateHash(bitmap: Bitmap): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val byteBuffer = ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(byteBuffer)
        digest.update(byteBuffer.array())
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun applyPhotometricNormalization(bitmap: Bitmap, profile: PhotometricProfileProto?): Bitmap {
        // 1. Convert to Linear Space (De-gamma)
        val linearPixels = deGamma(bitmap)
        
        // 2. Apply CCM if available, otherwise Statistical Color Constancy (Shades-of-Grey)
        val balancedPixels = if (profile != null && profile.hasForwardMatrix1()) {
            applyColorMatrix(linearPixels, profile.forwardMatrix1)
        } else {
            applyShadesOfGreyLinear(linearPixels, bitmap.width, bitmap.height)
        }
        
        // 3. Convert back to sRGB (Re-gamma)
        return reGamma(balancedPixels, bitmap.width, bitmap.height)
    }

    private fun applyColorMatrix(linear: FloatArray, matrix: ColorMatrixProto): FloatArray {
        val m = matrix.entriesList
        if (m.size < 9) return linear

        for (i in 0 until (linear.size / 3)) {
            val r = linear[i * 3]
            val g = linear[i * 3 + 1]
            val b = linear[i * 3 + 2]

            linear[i * 3] = r * m[0] + g * m[1] + b * m[2]
            linear[i * 3 + 1] = r * m[3] + g * m[4] + b * m[5]
            linear[i * 3 + 2] = r * m[6] + g * m[7] + b * m[8]
        }
        return linear
    }

    private fun deGamma(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        
        val linear = FloatArray(pixels.size * 3)
        for (i in pixels.indices) {
            val p = pixels[i]
            // Simple power-law de-gamma (approximation of sRGB)
            linear[i * 3] = (Color.red(p) / 255.0f).pow(2.2f)
            linear[i * 3 + 1] = (Color.green(p) / 255.0f).pow(2.2f)
            linear[i * 3 + 2] = (Color.blue(p) / 255.0f).pow(2.2f)
        }
        return linear
    }

    private fun applyShadesOfGreyLinear(linear: FloatArray, width: Int, height: Int, p: Int = 6): FloatArray {
        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0

        val numPixels = width * height
        for (i in 0 until numPixels) {
            sumR += linear[i * 3].toDouble().pow(p.toDouble())
            sumG += linear[i * 3 + 1].toDouble().pow(p.toDouble())
            sumB += linear[i * 3 + 2].toDouble().pow(p.toDouble())
        }

        val normR = (sumR / numPixels).pow(1.0 / p).toFloat()
        val normG = (sumG / numPixels).pow(1.0 / p).toFloat()
        val normB = (sumB / numPixels).pow(1.0 / p).toFloat()

        val avg = (normR + normG + normB) / 3.0f
        val gainR = if (normR > 0) avg / normR else 1f
        val gainG = if (normG > 0) avg / normG else 1f
        val gainB = if (normB > 0) avg / normB else 1f

        for (i in 0 until numPixels) {
            linear[i * 3] *= gainR
            linear[i * 3 + 1] *= gainG
            linear[i * 3 + 2] *= gainB
        }
        return linear
    }

    private fun reGamma(linear: FloatArray, width: Int, height: Int): Bitmap {
        val outPixels = IntArray(width * height)
        for (i in 0 until (width * height)) {
            val r = (linear[i * 3].pow(1.0f / 2.2f) * 255f).toInt().coerceIn(0, 255)
            val g = (linear[i * 3 + 1].pow(1.0f / 2.2f) * 255f).toInt().coerceIn(0, 255)
            val b = (linear[i * 3 + 2].pow(1.0f / 2.2f) * 255f).toInt().coerceIn(0, 255)
            outPixels[i] = Color.rgb(r, g, b)
        }
        return Bitmap.createBitmap(outPixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun applyShadesOfGrey(bitmap: Bitmap, p: Int = 6): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var sumR = 0.0
        var sumG = 0.0
        var sumB = 0.0

        for (pixel in pixels) {
            sumR += Color.red(pixel).toDouble().pow(p.toDouble())
            sumG += Color.green(pixel).toDouble().pow(p.toDouble())
            sumB += Color.blue(pixel).toDouble().pow(p.toDouble())
        }

        val normR = (sumR / pixels.size).pow(1.0 / p)
        val normG = (sumG / pixels.size).pow(1.0 / p)
        val normB = (sumB / pixels.size).pow(1.0 / p)

        val avg = (normR + normG + normB) / 3.0
        val gainR = (avg / normR).toFloat()
        val gainG = (avg / normG).toFloat()
        val gainB = (avg / normB).toFloat()

        val outPixels = IntArray(pixels.size)
        for (i in pixels.indices) {
            val r = (Color.red(pixels[i]) * gainR).toInt().coerceIn(0, 255)
            val g = (Color.green(pixels[i]) * gainG).toInt().coerceIn(0, 255)
            val b = (Color.blue(pixels[i]) * gainB).toInt().coerceIn(0, 255)
            outPixels[i] = Color.rgb(r, g, b)
        }

        return Bitmap.createBitmap(outPixels, width, height, Bitmap.Config.ARGB_8888)
    }
}
