package com.pricelens.ml.runtime.delegate

import org.tensorflow.lite.Delegate
import com.pricelens.core.common.logging.Logger
import com.pricelens.ml.runtime.parity.DelegateParityHarness
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DelegateManager @Inject constructor(
    private val delegateProvider: DelegateProvider,
    private val parityHarness: DelegateParityHarness,
    private val logger: Logger
) {
    /**
     * Selects the fastest delegate that passes the parity check.
     */
    fun selectBestDelegate(
        modelFile: File,
        goldenInputs: List<ByteBuffer>,
        referenceOutputs: List<FloatArray>
    ): Delegate? {
        // Try GPU
        delegateProvider.getGpuDelegate()?.let { gpu ->
            if (parityHarness.verifyDelegate(modelFile, gpu, goldenInputs, referenceOutputs)) {
                logger.i("DelegateManager", "GPU Delegate passed parity check")
                return gpu
            }
            logger.w("DelegateManager", "GPU Delegate failed parity check, falling back")
        }

        // Try NNAPI
        delegateProvider.getNnApiDelegate()?.let { nnapi ->
            if (parityHarness.verifyDelegate(modelFile, nnapi, goldenInputs, referenceOutputs)) {
                logger.i("DelegateManager", "NNAPI Delegate passed parity check")
                return nnapi
            }
            logger.w("DelegateManager", "NNAPI Delegate failed parity check, falling back")
        }

        // Default to CPU (XNNPACK)
        logger.i("DelegateManager", "No hardware acceleration passed parity check, using CPU")
        return null
    }
}
