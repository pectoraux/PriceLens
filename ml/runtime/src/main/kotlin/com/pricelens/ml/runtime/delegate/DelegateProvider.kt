package com.pricelens.ml.runtime.delegate

import org.tensorflow.lite.Delegate
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import com.pricelens.core.common.logging.Logger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DelegateProvider @Inject constructor(
    private val logger: Logger
) {
    fun getGpuDelegate(): Delegate? {
        return try {
            GpuDelegate().also {
                logger.i("DelegateProvider", "GPU Delegate created successfully")
            }
        } catch (e: Exception) {
            logger.e("DelegateProvider", "Failed to create GPU Delegate", e)
            null
        }
    }

    fun getNnApiDelegate(): Delegate? {
        return try {
            NnApiDelegate().also {
                logger.i("DelegateProvider", "NNAPI Delegate created successfully")
            }
        } catch (e: Exception) {
            logger.e("DelegateProvider", "Failed to create NNAPI Delegate", e)
            null
        }
    }

    /**
     * XNNPACK is often the best CPU fallback.
     */
    fun getXnnpackDelegate(): Delegate? {
        // TFLite 2.x often enables XNNPACK by default in Options, 
        // but an explicit delegate can be used if needed.
        return null // Placeholder for explicit XNNPACK if required
    }
}
