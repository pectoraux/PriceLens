package com.pricelens.feature.capture

import android.graphics.Bitmap
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pricelens.core.common.logging.Logger
import com.pricelens.core.common.ml.Quantization
import com.pricelens.core.data.local.db.entity.ObservationEntity
import com.pricelens.core.data.repository.DeviceProfileRepository
import com.pricelens.core.data.repository.ObservationRepository
import com.pricelens.core.geo.GeoManager
import com.pricelens.core.trust.CaptureSealer
import com.pricelens.core.trust.NonceRepository
import com.pricelens.core.attest.IntegrityManager
import com.pricelens.core.attest.AttestationManager
import com.pricelens.ml.pipeline.InferencePipeline
import com.pricelens.ml.pipeline.model.PipelineResult
import com.pricelens.ml.runtime.thermal.ThermalPolicyManager
import com.pricelens.feature.capture.camera.CameraSession
import com.pricelens.feature.capture.camera.toBitmapSafe
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface CaptureEvent {
    data class NavigateToReview(val observationId: String) : CaptureEvent
}

@HiltViewModel
class CaptureViewModel @Inject constructor(
    private val cameraSession: CameraSession,
    private val deviceProfileRepository: DeviceProfileRepository,
    private val nonceRepository: NonceRepository,
    private val integrityManager: IntegrityManager,
    private val attestationManager: AttestationManager,
    private val captureSealer: CaptureSealer,
    private val observationRepository: ObservationRepository,
    private val inferencePipeline: InferencePipeline,
    private val thermalPolicyManager: ThermalPolicyManager,
    private val geoManager: GeoManager,
    private val logger: Logger
) : ViewModel() {

    private val _isCalibrationMode = MutableStateFlow(false)
    val isCalibrationMode: StateFlow<Boolean> = _isCalibrationMode.asStateFlow()

    private val _analysisFrameRate = MutableStateFlow(30)
    val analysisFrameRate: StateFlow<Int> = _analysisFrameRate.asStateFlow()

    private val _recognitionResult = MutableStateFlow<PipelineResult?>(null)
    val recognitionResult: StateFlow<PipelineResult?> = _recognitionResult.asStateFlow()

    private val _coachingHint = MutableStateFlow<String?>(null)
    val coachingHint: StateFlow<String?> = _coachingHint.asStateFlow()

    private val _geohash = MutableStateFlow("unknown")
    val geohash: StateFlow<String> = _geohash.asStateFlow()

    private val _deviceClassId = MutableStateFlow("pixel_8_pro")
    val deviceClassId: StateFlow<String> = _deviceClassId.asStateFlow()

    private val _events = MutableSharedFlow<CaptureEvent>()
    val events: SharedFlow<CaptureEvent> = _events.asSharedFlow()

    private var lastAnalysisTime = 0L

    init {
        viewModelScope.launch {
            val location = geoManager.getCurrentLocation()
            _geohash.value = location?.let { geoManager.getGeohash6(it) } ?: "unknown"
            
            val profile = deviceProfileRepository.deviceProfile.first()
            _deviceClassId.value = profile.deviceClassId
        }

        thermalPolicyManager.startMonitoring()
        thermalPolicyManager.addListener(object : ThermalPolicyManager.ThermalStatusListener {
            override fun onThermalStatusChanged(status: Int) {
                adjustFrameRate(status)
            }
        })

        observeFrames()
    }

    private fun observeFrames() {
        viewModelScope.launch {
            cameraSession.frames.collectLatest { analyzableFrame ->
                try {
                    val currentTime = System.currentTimeMillis()
                    val intervalMs = 1000L / _analysisFrameRate.value
                    
                    if (currentTime - lastAnalysisTime >= intervalMs) {
                        lastAnalysisTime = currentTime
                        
                        val bitmap = analyzableFrame.image.toBitmapSafe()
                        if (bitmap != null) {
                            processFrame(bitmap, analyzableFrame.imuWindow, analyzableFrame.metadata)
                        }
                    }
                } finally {
                    analyzableFrame.image.close()
                }
            }
        }
    }

    private suspend fun processFrame(
        bitmap: Bitmap,
        imuWindow: List<Triple<Float, Float, Float>>?,
        metadata: com.pricelens.feature.capture.camera.model.CaptureResultMetadata
    ) {
        val profile = deviceProfileRepository.deviceProfile.first()
        val result = inferencePipeline.processFrame(
            rawFrame = bitmap,
            deviceProfile = profile,
            zoom = 1.0f, // TODO: Get zoom from CameraSession
            imuWindow = imuWindow,
            priors = emptyMap()
        ).let { res ->
            // Attach raw sensor metadata for sealing later
            res.copy(
                metadata = res.metadata + mapOf(
                    "exposureTimeNs" to (metadata.exposureTimeNs ?: 0L),
                    "sensitivity" to (metadata.sensitivity ?: 0),
                    "lensAperture" to (metadata.lensAperture ?: 0f),
                    "focalLength" to (metadata.focalLength ?: 0f),
                    "focusDistance" to (metadata.focusDistance ?: 0f)
                ),
                imuWindow = imuWindow
            )
        }
        _recognitionResult.value = result
        _coachingHint.value = determineCoachingHint(result.qualityIssues)
    }

    private fun determineCoachingHint(issues: List<String>): String? {
        return when {
            issues.contains("TOO_BLURRY") -> "Hold still"
            issues.contains("UNDER_EXPOSED") -> "Move to better light"
            issues.contains("OVER_EXPOSED") -> "Too bright"
            issues.contains("NO_OBJECTS_DETECTED") -> "Point at a food item"
            else -> null
        }
    }

    fun setupCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        viewModelScope.launch {
            cameraSession.bind(lifecycleOwner, previewView)
        }
    }

    private fun adjustFrameRate(thermalStatus: Int) {
        val newRate = when (thermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> 30
            PowerManager.THERMAL_STATUS_LIGHT -> 20
            PowerManager.THERMAL_STATUS_MODERATE -> 10
            PowerManager.THERMAL_STATUS_SEVERE -> 5
            PowerManager.THERMAL_STATUS_CRITICAL -> 1
            else -> 30
        }
        _analysisFrameRate.value = newRate
        logger.i("CaptureViewModel", "Adjusted analysis frame rate to $newRate FPS due to thermal status $thermalStatus")
    }

    fun setCalibrationMode(enabled: Boolean) {
        _isCalibrationMode.value = enabled
    }

    fun onAbstainOptionSelected(option: String?) {
        val currentResult = _recognitionResult.value ?: return
        if (option != null) {
            // User selected a candidate, proceed with capture using this label
            _recognitionResult.value = currentResult.copy(label = option, abstained = false)
            onCaptureTapped(_deviceClassId.value, _geohash.value)
        } else {
            // User clicked "None of these", navigate to manual picker in Review
            viewModelScope.launch {
                val observationId = performSealing(currentResult, "item.unknown", _deviceClassId.value, _geohash.value)
                if (observationId != null) {
                    _events.emit(CaptureEvent.NavigateToReview(observationId))
                }
            }
        }
    }

    fun onCaptureTapped(deviceClassId: String, geohash: String) {
        if (_isCalibrationMode.value) {
            // Handle white-sheet calibration logic
            return
        }
        viewModelScope.launch {
            val result = _recognitionResult.value ?: return@launch
            val observationId = performSealing(result, result.label, deviceClassId, geohash)
            if (observationId != null) {
                _events.emit(CaptureEvent.NavigateToReview(observationId))
            }
        }
    }

    private suspend fun performSealing(
        result: PipelineResult,
        label: String?,
        deviceClassId: String,
        geohash: String
    ): String? {
        return try {
            val frameHash = result.frameHash ?: "unknown"
            val evidenceHash = result.evidenceHash ?: frameHash
            
            val nonce = try {
                nonceRepository.fetchNonce()
            } catch (e: Exception) {
                logger.w("CaptureViewModel", "Failed to fetch nonce, using offline placeholder")
                "offline_fallback_${System.currentTimeMillis()}"
            }
            
            val integrityToken = try {
                integrityManager.requestIntegrityToken(nonce)
            } catch (e: Exception) {
                null
            }
            
            attestationManager.ensureIdentityKey()
            
            val profile = deviceProfileRepository.deviceProfile.first()
            
            val (sealJson, signature) = captureSealer.seal(
                nonce = nonce,
                frameHash = frameHash,
                evidenceHash = evidenceHash,
                integrityToken = integrityToken,
                deviceClassId = deviceClassId,
                profileId = profile.profileId,
                sensorMetadata = com.pricelens.core.trust.SensorMetadata(
                    exposureTimeNs = result.metadata["exposureTimeNs"] as? Long ?: 0L,
                    sensitivity = result.metadata["sensitivity"] as? Int ?: 0,
                    aperture = result.metadata["lensAperture"] as? Float ?: 0f,
                    focalLength = result.metadata["focalLength"] as? Float ?: 0f
                ),
                imuWindow = result.imuWindow ?: emptyList(),
                modelVersions = mapOf("recognizer" to "v1.0")
            )
            
            val observationId = UUID.randomUUID().toString()
            val draft = ObservationEntity(
                id = observationId,
                itemId = label,
                geohash = geohash,
                priceMinor = 0,
                currencyCode = "KES",
                unit = "kg",
                quantity = 1.0,
                timestamp = System.currentTimeMillis(),
                captureSealJson = sealJson,
                signature = signature,
                syncStatus = "DRAFT",
                predictedItemId = result.label,
                predictedConfidence = result.confidence,
                embedding = result.embedding?.let { Quantization.quantizeToInt8(it) },
                abstained = result.abstained
            )
            
            observationRepository.saveDraft(draft)
            observationRepository.saveEvidence(observationId, result.redactedFrame ?: result.canonicalFrame)
            logger.i("CaptureViewModel", "Draft saved: $observationId")
            observationId
        } catch (e: Exception) {
            logger.e("CaptureViewModel", "Failed to seal frame", e)
            null
        }
    }

    override fun onCleared() {
        super.onCleared()
        cameraSession.release()
    }
}
