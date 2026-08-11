package com.pricelens.ml.deviceprofile.photometric

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.params.ColorSpaceTransform
import com.pricelens.core.data.model.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PhotometricProfileExtractor @Inject constructor() {

    fun extract(characteristics: CameraCharacteristics): PhotometricProfileProto {
        return photometricProfileProto {
            characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM1)?.let { transform ->
                colorTransform1 = transform.toProto()
            }
            characteristics.get(CameraCharacteristics.SENSOR_COLOR_TRANSFORM2)?.let { transform ->
                colorTransform2 = transform.toProto()
            }
            characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX1)?.let { transform ->
                forwardMatrix1 = transform.toProto()
            }
            characteristics.get(CameraCharacteristics.SENSOR_FORWARD_MATRIX2)?.let { transform ->
                forwardMatrix2 = transform.toProto()
            }
            characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM1)?.let { transform ->
                calibrationTransform1 = transform.toProto()
            }
            characteristics.get(CameraCharacteristics.SENSOR_CALIBRATION_TRANSFORM2)?.let { transform ->
                calibrationTransform2 = transform.toProto()
            }

            characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT1)?.let { ill ->
                referenceIlluminant1 = ill.toInt()
            }
            characteristics.get(CameraCharacteristics.SENSOR_REFERENCE_ILLUMINANT2)?.let { ill ->
                referenceIlluminant2 = ill.toInt()
            }
        }
    }

    private fun ColorSpaceTransform.toProto(): ColorMatrixProto {
        val rationals = arrayOfNulls<android.util.Rational>(9)
        this.copyElements(rationals, 0)
        val entryList = rationals.map { r ->
            r?.let { it.numerator.toFloat() / it.denominator.toFloat() } ?: 0f
        }
        return colorMatrixProto {
            entries.addAll(entryList)
        }
    }
}
