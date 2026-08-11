package com.pricelens.feature.capture.camera

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as AndroidCameraManager
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import com.pricelens.core.common.logging.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCamera2Interop::class)
@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as AndroidCameraManager

    suspend fun getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                continuation.resume(future.get())
            }, { it.run() })
        }
    }

    fun selectOptimalCamera(cameraProvider: ProcessCameraProvider): CameraSelector? {
        val availableCameraInfos = cameraProvider.availableCameraInfos
        if (availableCameraInfos.isEmpty()) return null

        val bestCamera = availableCameraInfos.filter { info ->
            val camera2Info = Camera2CameraInfo.from(info)
            camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_FACING) == 
                CameraCharacteristics.LENS_FACING_BACK
        }.maxByOrNull { info ->
            val camera2Info = Camera2CameraInfo.from(info)
            
            // C-02: Prefer sensors with standard focal length and calibration
            val focalLengths = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val primaryFocal = focalLengths?.firstOrNull() ?: 4.0f
            
            val physicalSize = camera2Info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            
            val hasCalibration = camera2Info.getCameraCharacteristic(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION) != null
            val sensorArea = (physicalSize?.width ?: 0f) * (physicalSize?.height ?: 0f)
            
            // Heuristic: scale score by focal length (avoiding ultra-wide < 3.5mm)
            val focalBonus = if (primaryFocal > 3.5f) 500f else 0f
            val calibrationBonus = if (hasCalibration) 1000f else 0f
            
            sensorArea + focalBonus + calibrationBonus
        }

        return bestCamera?.let { targetInfo ->
            CameraSelector.Builder()
                .addCameraFilter { cameras ->
                    cameras.filter { it == targetInfo }
                }
                .build()
        }
    }

    fun getPhysicalCameraId(cameraInfo: androidx.camera.core.CameraInfo): String {
        return Camera2CameraInfo.from(cameraInfo).cameraId
    }
}
