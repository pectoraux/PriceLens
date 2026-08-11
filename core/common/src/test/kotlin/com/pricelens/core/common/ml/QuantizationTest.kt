package com.pricelens.core.common.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QuantizationTest {

    @Test
    fun `quantization round-trip should preserve magnitude`() {
        val original = floatArrayOf(-1.0f, -0.5f, 0.0f, 0.5f, 1.0f)
        val quantized = Quantization.quantizeToInt8(original)
        val dequantized = Quantization.dequantizeFromInt8(quantized)

        // Tolerance for INT8 quantization: 1/127 ≈ 0.0078
        for (i in original.indices) {
            assertEquals(original[i], dequantized[i], 0.01f)
        }
    }

    @Test
    fun `quantization should handle extreme values`() {
        val extreme = floatArrayOf(-2.0f, 2.0f)
        val quantized = Quantization.quantizeToInt8(extreme)
        
        assertEquals((-128).toByte(), quantized[0])
        assertEquals(127.toByte(), quantized[1])
    }
}
