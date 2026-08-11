package com.pricelens.ml.runtime.parity

import org.tensorflow.lite.Delegate
import org.tensorflow.lite.Interpreter
import com.pricelens.core.common.logging.Logger
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class DelegateParityHarness @Inject constructor(
    private val logger: Logger
) {
    /**
     * Verifies if a delegate produces numerically consistent output compared to a reference.
     * threshold: Mean Cosine Distance should be < 1e-3
     */
    fun verifyDelegate(
        modelFile: File,
        delegate: Delegate,
        goldenInputs: List<ByteBuffer>,
        referenceOutputs: List<FloatArray>
    ): Boolean {
        val options = Interpreter.Options().apply {
            addDelegate(delegate)
        }
        val interpreter = try {
            Interpreter(modelFile, options)
        } catch (e: Exception) {
            logger.e("ParityHarness", "Failed to create interpreter with delegate", e)
            return false
        }

        var totalCosineDistance = 0.0
        val outputBuffer = ByteBuffer.allocateDirect(referenceOutputs[0].size * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        for (i in goldenInputs.indices) {
            outputBuffer.rewind()
            interpreter.run(goldenInputs[i], outputBuffer)
            
            val actualOutput = FloatArray(referenceOutputs[i].size)
            outputBuffer.rewind()
            outputBuffer.asFloatBuffer().get(actualOutput)
            
            totalCosineDistance += calculateCosineDistance(actualOutput, referenceOutputs[i])
        }

        val meanDistance = totalCosineDistance / goldenInputs.size
        logger.i("ParityHarness", "Mean Cosine Distance: $meanDistance")

        interpreter.close()
        return meanDistance < 1e-3
    }

    private fun calculateCosineDistance(a: FloatArray, b: FloatArray): Double {
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dotProduct += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val similarity = dotProduct / (sqrt(normA) * sqrt(normB))
        return 1.0 - similarity
    }
}
