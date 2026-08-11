package com.pricelens.ml.deviceprofile.optical

import android.hardware.camera2.CameraCharacteristics
import com.pricelens.core.data.model.OpticalProfileProto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpticalProfileExtractor @Inject constructor() {

    fun extract(
        characteristics: CameraCharacteristics,
        cameraId: String,
        targetWidth: Int,
        targetHeight: Int
    ): OpticalProfileProto {
        val intrinsic = characteristics.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)
        val activeArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        val builder = OpticalProfileProto.newBuilder().setCameraId(cameraId)

        // 1. Intrinsics
        if (intrinsic != null && intrinsic.size >= 5 && activeArray != null) {
            val scaleX = targetWidth.toFloat() / activeArray.width()
            val scaleY = targetHeight.toFloat() / activeArray.height()

            builder.setSource("REPORTED")
                .setFX(intrinsic[0] * scaleX)
                .setFY(intrinsic[1] * scaleY)
                .setCX(intrinsic[2] * scaleX)
                .setCY(intrinsic[3] * scaleY)
                .setS(intrinsic[4])
        } else {
            val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
            val focalLength = focalLengths?.get(0) ?: 0f
            val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val pixelArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)

            if (physicalSize != null && pixelArraySize != null && activeArray != null) {
                val fx = focalLength * (pixelArraySize.width / physicalSize.width)
                val fy = focalLength * (pixelArraySize.height / physicalSize.height)
                val scaleX = targetWidth.toFloat() / pixelArraySize.width
                val scaleY = targetHeight.toFloat() / pixelArraySize.height

                builder.setSource("DERIVED")
                    .setFX(fx * scaleX)
                    .setFY(fy * scaleY)
                    .setCX(activeArray.width() / 2f * scaleX)
                    .setCY(activeArray.height() / 2f * scaleY)
                    .setS(0f)
                    .setSensorPhysicalWidthMm(physicalSize.width)
                    .setSensorPhysicalHeightMm(physicalSize.height)
            } else {
                // Record degradation if essential info is missing
                builder.setSource("FALLBACK")
            }
        }

        // 2. Distortion
        val distortion = characteristics.get(CameraCharacteristics.LENS_DISTORTION)
        if (distortion != null) {
            builder.addAllDistortion(distortion.toList())
        } else {
            val radial = characteristics.get(CameraCharacteristics.LENS_RADIAL_DISTORTION)
            if (radial != null) {
                builder.addAllDistortion(radial.toList())
            }
        }

        // 3. Sensor Info
        val exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE)
        if (exposureRange != null) {
            builder.setMinExposureNs(exposureRange.lower)
            builder.setMaxExposureNs(exposureRange.upper)
        }
        val sensitivityRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE)
        if (sensitivityRange != null) {
            builder.setMinIso(sensitivityRange.lower)
            builder.setMaxIso(sensitivityRange.upper)
        }

        // 4. Quad-Bayer / Binning detection (D-01)
        val pixelArray = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
        val activeArraySize = characteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        if (pixelArray != null && activeArraySize != null) {
            val totalPixels = pixelArray.width.toLong() * pixelArray.height
            if (totalPixels > 40_000_000) { // 40MP+ is almost always Quad-Bayer/Nonapixel
                builder.setIsBinned(true)
            }
        }

        // 5. Derived FOV
        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
        val physicalSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
        if (focalLengths != null && focalLengths.isNotEmpty() && physicalSize != null) {
            val hFov = 2 * Math.toDegrees(Math.atan((physicalSize.width / (2 * focalLengths[0])).toDouble()))
            builder.setHFovDeg(hFov.toFloat())
        }

        return builder.build()
    }
}
