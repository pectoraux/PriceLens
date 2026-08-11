package com.pricelens.core.common.ml

/**
 * Utilities for INT8 quantization of ML embeddings (J-01).
 * Optimized for L2-normalized embeddings in range [-1.0, 1.0].
 */
object Quantization {

    /**
     * Quantizes a FloatArray into a ByteArray using symmetric scale-only quantization.
     */
    fun quantizeToInt8(embedding: FloatArray): ByteArray {
        val bytes = ByteArray(embedding.size)
        for (i in embedding.indices) {
            // Map [-1.0, 1.0] to [-128, 127]
            bytes[i] = (embedding[i] * 127.0f).toInt().coerceIn(-128, 127).toByte()
        }
        return bytes
    }

    /**
     * Dequantizes a ByteArray back into a FloatArray.
     */
    fun dequantizeFromInt8(bytes: ByteArray): FloatArray {
        val floats = FloatArray(bytes.size)
        for (i in bytes.indices) {
            floats[i] = bytes[i].toFloat() / 127.0f
        }
        return floats
    }
}
