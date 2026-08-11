package com.pricelens.ml.deviceprofile.compute

import com.pricelens.core.common.logging.Logger
import com.pricelens.core.data.model.ComputeProfileProto
import com.pricelens.core.data.model.computeProfileProto
import com.pricelens.ml.runtime.delegate.DelegateProvider
import com.pricelens.ml.runtime.interpreter.LiteRtInterpreter
import org.tensorflow.lite.Delegate
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

@Singleton
class ComputeProfileExtractor @Inject constructor(
    private val delegateProvider: DelegateProvider,
    private val logger: Logger
) {

    /**
     * Profiles the device to determine the optimal ML delegate.
     * In a real implementation, this would run a small benchmark model.
     */
    suspend fun profile(benchmarkModelFile: File?): ComputeProfileProto {
        val results = mutableMapOf<String, Long>()

        // 1. Profile CPU (Baseline)
        results["CPU"] = benchmark(benchmarkModelFile, null)

        // 2. Profile GPU
        val gpuDelegate = delegateProvider.getGpuDelegate()
        if (gpuDelegate != null) {
            results["GPU"] = benchmark(benchmarkModelFile, gpuDelegate)
        }

        // 3. Profile NNAPI
        val nnapiDelegate = delegateProvider.getNnApiDelegate()
        if (nnapiDelegate != null) {
            results["NNAPI"] = benchmark(benchmarkModelFile, nnapiDelegate)
        }

        // Select the delegate with the minimum latency
        val bestDelegate = results.minByOrNull { it.value }?.key ?: "CPU"
        
        logger.i("ComputeProfile", "Benchmarking complete. Best delegate: $bestDelegate. Latencies: $results")

        return computeProfileProto {
            selectedDelegate = bestDelegate
        }
    }

    private fun benchmark(modelFile: File?, delegate: Delegate?): Long {
        if (modelFile == null || !modelFile.exists()) {
            // Return mock latency based on delegate type if no model is provided
            return when (delegate) {
                null -> 100L // CPU
                is GpuDelegate -> 40L
                is NnApiDelegate -> 60L
                else -> 100L
            }
        }
        
        // Real benchmarking logic would go here:
        // Initialize interpreter with delegate, run inference N times, return p95
        return 50L // Placeholder
    }
}
