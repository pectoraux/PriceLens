package com.pricelens.ml.detect.interpreter

import android.graphics.Bitmap
import android.graphics.RectF
import com.pricelens.core.common.logging.Logger
import com.pricelens.core.data.repository.ModelRepository
import com.pricelens.ml.pipeline.contract.Detector
import com.pricelens.ml.pipeline.model.CanonicalFrame
import com.pricelens.ml.pipeline.model.Detection
import com.pricelens.ml.runtime.interpreter.LiteRtInterpreter
import com.pricelens.ml.runtime.util.TensorImageConverter
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtDetector @Inject constructor(
    private val modelRepository: ModelRepository,
    private val interpreter: LiteRtInterpreter,
    private val logger: Logger
) : Detector {

    private var isInitialized = false

    private fun ensureInitialized() {
        if (isInitialized) return
        val modelFile = modelRepository.getModelFile("detector")
        if (modelFile.exists()) {
            val options = Interpreter.Options().apply {
                setUseXNNPACK(true)
            }
            interpreter.initialize(modelFile, options)
            isInitialized = true
        }
    }

    override suspend fun detect(frame: CanonicalFrame): List<Detection> {
        ensureInitialized()
        if (!isInitialized) return emptyList()

        val inputBuffer = TensorImageConverter.bitmapToBuffer(frame.bitmap, 640, 640) // YOLO-World size
        val outputBuffer = ByteBuffer.allocateDirect(1 * 8400 * 6 * 4) // Example YOLOv8 shape
        outputBuffer.order(ByteOrder.nativeOrder())

        interpreter.run(inputBuffer, outputBuffer)
        outputBuffer.rewind()

        // Post-processing: Decode boxes and NMS
        return decodeYoloOutput(outputBuffer)
    }

    private fun decodeYoloOutput(buffer: ByteBuffer): List<Detection> {
        val rawDetections = mutableListOf<Detection>()
        val floatBuffer = buffer.asFloatBuffer()
        
        val numBoxes = 8400
        val numElements = 6 // cx, cy, w, h, score, class
        
        for (i in 0 until numBoxes) {
            val cx = floatBuffer.get(i * numElements + 0)
            val cy = floatBuffer.get(i * numElements + 1)
            val w = floatBuffer.get(i * numElements + 2)
            val h = floatBuffer.get(i * numElements + 3)
            val score = floatBuffer.get(i * numElements + 4)
            val classId = floatBuffer.get(i * numElements + 5).toInt()
            
            if (score > 0.25f) { // Confidence threshold (E-02)
                val left = (cx - w / 2f)
                val top = (cy - h / 2f)
                val right = (cx + w / 2f)
                val bottom = (cy + h / 2f)
                
                rawDetections.add(
                    Detection(
                        boundingBox = RectF(left, top, right, bottom),
                        label = null, // Class-agnostic detection; label assigned by retriever
                        score = score,
                        isPerson = classId == 0 // Example person class index
                    )
                )
            }
        }
        
        logger.i("LiteRtDetector", "Decoded ${rawDetections.size} raw boxes")
        return nonMaximumSuppression(rawDetections, 0.45f)
    }

    private fun nonMaximumSuppression(detections: List<Detection>, iouThreshold: Float): List<Detection> {
        val sorted = detections.sortedByDescending { it.score }
        val selected = mutableListOf<Detection>()
        val suppressed = BooleanArray(sorted.size)
        
        for (i in sorted.indices) {
            if (suppressed[i]) continue
            val a = sorted[i]
            selected.add(a)
            
            for (j in i + 1 until sorted.size) {
                if (suppressed[j]) continue
                val b = sorted[j]
                if (calculateIou(a.boundingBox, b.boundingBox) > iouThreshold) {
                    suppressed[j] = true
                }
            }
        }
        return selected
    }

    private fun calculateIou(a: RectF, b: RectF): Float {
        val intersection = RectF()
        if (!intersection.setIntersect(a, b)) return 0f
        
        val intersectArea = intersection.width() * intersection.height()
        val unionArea = (a.width() * a.height()) + (b.width() * b.height()) - intersectArea
        return if (unionArea > 0) intersectArea / unionArea else 0f
    }
}
