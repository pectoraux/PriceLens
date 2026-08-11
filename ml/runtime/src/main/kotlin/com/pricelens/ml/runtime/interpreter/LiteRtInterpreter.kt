package com.pricelens.ml.runtime.interpreter

import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.Interpreter.Options
import com.pricelens.core.common.logging.Logger
import com.pricelens.ml.runtime.delegate.DelegateManager
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject

class LiteRtInterpreter @Inject constructor(
    private val delegateManager: DelegateManager,
    private val logger: Logger
) {
    private var interpreter: Interpreter? = null

    fun initialize(
        modelFile: File,
        options: Options,
        goldenInputs: List<ByteBuffer>? = null,
        referenceOutputs: List<FloatArray>? = null
    ) {
        try {
            if (goldenInputs != null && referenceOutputs != null) {
                val bestDelegate = delegateManager.selectBestDelegate(modelFile, goldenInputs, referenceOutputs)
                bestDelegate?.let { options.addDelegate(it) }
            }
            
            interpreter = Interpreter(modelFile, options)
            logger.i("LiteRtInterpreter", "Interpreter initialized for ${modelFile.name}")
        } catch (e: Exception) {
            logger.e("LiteRtInterpreter", "Failed to initialize interpreter", e)
        }
    }

    fun run(input: ByteBuffer, output: ByteBuffer) {
        interpreter?.run(input, output) ?: logger.e("LiteRtInterpreter", "Interpreter not initialized")
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}
