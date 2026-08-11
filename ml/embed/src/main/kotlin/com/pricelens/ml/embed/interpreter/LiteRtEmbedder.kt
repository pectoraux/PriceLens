package com.pricelens.ml.embed.interpreter

import android.graphics.Bitmap
import com.pricelens.core.common.logging.Logger
import com.pricelens.core.data.repository.ModelRepository
import com.pricelens.ml.pipeline.contract.Embedder
import com.pricelens.ml.pipeline.model.CanonicalFrame
import com.pricelens.ml.pipeline.model.Detection
import com.pricelens.ml.runtime.interpreter.LiteRtInterpreter
import com.pricelens.ml.runtime.util.TensorImageConverter
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class LiteRtEmbedder @Inject constructor(
    private val modelRepository: ModelRepository,
    private val interpreter: LiteRtInterpreter,
    private val logger: Logger
) : Embedder {

    private var isInitialized = false

    private fun ensureInitialized() {
        if (isInitialized) return
        val modelFile = modelRepository.getModelFile("embedder")
        if (modelFile.exists()) {
            val options = Interpreter.Options().apply {
                setUseXNNPACK(true)
            }
            interpreter.initialize(modelFile, options)
            isInitialized = true
        }
    }

    override suspend fun embed(frame: CanonicalFrame, detection: Detection): FloatArray {
        ensureInitialized()
        if (!isInitialized) return FloatArray(512)

        // Crop bitmap to detection bounding box
        val crop = cropBitmap(frame.bitmap, detection)
        val inputBuffer = TensorImageConverter.bitmapToBuffer(crop, 224, 224) // CLIP input size
        val outputBuffer = ByteBuffer.allocateDirect(1 * 512 * 4)
        outputBuffer.order(ByteOrder.nativeOrder())

        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        val embedding = FloatArray(512)
        outputBuffer.asFloatBuffer().get(embedding)

        return normalize(embedding)
    }

    private fun cropBitmap(bitmap: Bitmap, detection: Detection): Bitmap {
        val rect = detection.boundingBox
        val x = rect.left.toInt().coerceIn(0, bitmap.width - 1)
        val y = rect.top.toInt().coerceIn(0, bitmap.height - 1)
        val width = rect.width().toInt().coerceAtMost(bitmap.width - x)
        val height = rect.height().toInt().coerceAtMost(bitmap.height - y)
        
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    private fun normalize(embedding: FloatArray): FloatArray {
        var sumSquares = 0.0f
        for (v in embedding) sumSquares += v * v
        val norm = sqrt(sumSquares)
        if (norm < 1e-6) return embedding
        
        for (i in embedding.indices) embedding[i] /= norm
        return embedding
    }
}
