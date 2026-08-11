package com.pricelens.core.geo

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.LocationManager
import com.pricelens.core.common.logging.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GnssIntegrityChecker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * Assesses GNSS integrity based on satellite C/N0 distributions.
     * High variance and realistic signal strengths suggest real hardware.
     */
    @SuppressLint("MissingPermission")
    fun assessIntegrity(onResult: (Float) -> Unit) {
        val callback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                val satCount = status.satelliteCount
                if (satCount == 0) {
                    onResult(0.1f) // Suspiciously zero satellites
                    return
                }

                var totalCn0 = 0f
                val cn0List = mutableListOf<Float>()
                for (i in 0 until satCount) {
                    val cn0 = status.getCn0DbHz(i)
                    totalCn0 += cn0
                    cn0List.add(cn0)
                }

                // Realistic distribution: should not be perfectly uniform
                val mean = totalCn0 / satCount
                val variance = cn0List.map { (it - mean) * (it - mean) }.sum() / satCount
                
                logger.i("GnssIntegrity", "Satellites: $satCount, Mean CN0: $mean, Var: $variance")

                // Simple heuristic: very low variance is suspicious for a spoofer
                val integrityScore = if (variance < 1.0f) 0.3f else 1.0f
                onResult(integrityScore)
                
                locationManager.unregisterGnssStatusCallback(this)
            }
        }
        
        try {
            locationManager.registerGnssStatusCallback(callback, null)
        } catch (e: Exception) {
            onResult(0.5f) // Error case
        }
    }
}
