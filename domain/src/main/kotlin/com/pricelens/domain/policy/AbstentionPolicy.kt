package com.pricelens.domain.policy

data class RecognitionConfidence(
    val top1Label: String,
    val top1Probability: Float,
    val top2Probability: Float,
    val deviceConfidence: DeviceConfidence = DeviceConfidence.HIGH,
    val qualityFailed: Boolean = false
)

enum class DeviceConfidence {
    LOW, MEDIUM, HIGH
}

object AbstentionPolicy {

    /**
     * Determines if the system should abstain from making a confident prediction.
     */
    fun shouldAbstain(confidence: RecognitionConfidence): Boolean {
        if (confidence.qualityFailed) return true
        
        // 1. Low absolute confidence
        if (confidence.top1Probability < Thresholds.TAU_LABEL) return true
        
        // 2. Ambiguity between top two candidates
        if (confidence.top1Probability - confidence.top2Probability < Thresholds.MARGIN_MIN) return true
        
        // 3. Low device confidence requires higher probability
        if (confidence.deviceConfidence == DeviceConfidence.LOW && 
            confidence.top1Probability < Thresholds.LOW_DEVICE_RECOGNITION_THRESHOLD) return true
            
        return false
    }
}
