package com.pricelens.ml.runtime.util

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

object TensorImageConverter {

    /**
     * Converts a Bitmap to a ByteBuffer for LiteRT input.
     * Normalized to [0, 1] or [-1, 1] based on parameters.
     */
    fun bitmapToBuffer(
        bitmap: Bitmap,
        width: Int,
        height: Int,
        mean: Float = 0f,
        std: Float = 255f
    ): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * width * height * 3)
        buffer.order(ByteOrder.nativeOrder())
        
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
        val pixels = IntArray(width * height)
        scaledBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        for (pixel in pixels) {
            val r = (pixel shr 16 and 0xFF).toFloat()
            val g = (pixel shr 8 and 0xFF).toFloat()
            val b = (pixel and 0xFF).toFloat()

            buffer.putFloat((r - mean) / std)
            buffer.putFloat((g - mean) / std)
            buffer.putFloat((b - mean) / std)
        }
        
        return buffer
    }
}
