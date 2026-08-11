package com.pricelens.core.geo

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.pricelens.domain.policy.Thresholds
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class GeoManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null).await()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if travel from lastLocation to newLocation is plausible (H-06).
     */
    fun isTravelPlausible(newLocation: Location, lastLocation: Location?, lastTimeMs: Long): Float {
        if (lastLocation == null) return 1.0f

        val distanceKm = calculateDistanceKm(
            newLocation.latitude, newLocation.longitude,
            lastLocation.latitude, lastLocation.longitude
        )
        val timeDeltaHours = (System.currentTimeMillis() - lastTimeMs) / (1000.0 * 60 * 60)
        if (timeDeltaHours <= 0) return 0.1f // Impossible time delta

        val speedKmh = distanceKm / timeDeltaHours

        return when {
            speedKmh > Thresholds.MAX_TRAVEL_SPEED_KMH -> 0.0f // Impossible
            speedKmh > Thresholds.SUSPICIOUS_TRAVEL_SPEED_KMH -> 0.3f // Suspicious
            else -> 1.0f
        }
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Earth radius
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun getGeohash6(location: Location): String {
        return encodeGeohash(location.latitude, location.longitude, 6)
    }

    fun getGeoConfidence(location: Location?): Float {
        if (location == null) return 0.0f
        
        var confidence = 1.0f
        
        // 1. Mock Detection (H-06)
        if (location.isFromMockProvider) {
            confidence *= 0.05f // Extreme penalty for mock
        }
        
        // 2. GNSS Plausibility stub (H-06)
        if (!checkGnssPlausibility(location)) {
            confidence *= 0.3f
        }

        // 3. Accuracy Scaling
        // Coarse geohash-6 is ~1.2km. If accuracy > 500m, confidence drops.
        val accuracy = location.accuracy
        confidence *= when {
            accuracy <= 30f -> 1.0f
            accuracy <= 100f -> 0.9f
            accuracy <= 500f -> 0.7f
            accuracy <= 1500f -> 0.4f
            else -> 0.1f
        }
        
        // 3. Age Check (Stale locations are risky)
        val ageMs = System.currentTimeMillis() - location.time
        if (ageMs > 300_000) { // 5 minutes
            confidence *= 0.5f
        }
        
        return confidence.coerceIn(0f, 1.0f)
    }

    /**
     * Basic Geohash encoding logic.
     */
    private fun encodeGeohash(latitude: Double, longitude: Double, precision: Int): String {
        val base32 = "0123456789bcdefghjkmnpqrstuvwxyz"
        val latRange = doubleArrayOf(-90.0, 90.0)
        val lonRange = doubleArrayOf(-180.0, 180.0)
        val geohash = StringBuilder()
        var isEven = true
        var bit = 0
        var ch = 0

        while (geohash.length < precision) {
            val mid: Double
            if (isEven) {
                mid = (lonRange[0] + lonRange[1]) / 2
                if (longitude > mid) {
                    ch = ch or (1 shl (4 - bit))
                    lonRange[0] = mid
                } else {
                    lonRange[1] = mid
                }
            } else {
                mid = (latRange[0] + latRange[1]) / 2
                if (latitude > mid) {
                    ch = ch or (1 shl (4 - bit))
                    latRange[0] = mid
                } else {
                    latRange[1] = mid
                }
            }

            isEven = !isEven
            if (bit < 4) {
                bit++
            } else {
                geohash.append(base32[ch])
                bit = 0
                ch = 0
            }
        }
        return geohash.toString()
    }

    private fun checkGnssPlausibility(location: Location): Boolean {
        // Placeholder for GNSS C/N0 distribution check (H-06)
        // In a real implementation, we'd use GnssStatus listener
        return true
    }
}
