package com.pricelens.core.trust

import com.pricelens.core.attest.AttestationManager
import com.pricelens.core.geo.GeoManager
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.security.Signature
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class CaptureSeal(
    val nonce: String,
    val frameHash: String,
    val evidenceHash: String,
    val integrityToken: String?,
    val geohash6: String,
    val geoConfidence: Float,
    val timestamp: Long,
    val deviceClassId: String,
    val profileId: String,
    val keyTier: String,
    val sensorMetadata: SensorMetadata,
    val imuWindow: List<Triple<Float, Float, Float>>,
    val modelVersions: Map<String, String>
)

@Serializable
data class SensorMetadata(
    val exposureTimeNs: Long,
    val sensitivity: Int,
    val aperture: Float,
    val focalLength: Float
)

@Singleton
class CaptureSealer @Inject constructor(
    private val geoManager: GeoManager,
    private val attestationManager: AttestationManager,
    private val keyStore: KeyStore
) {
    suspend fun seal(
        nonce: String,
        frameHash: String,
        evidenceHash: String,
        integrityToken: String?,
        deviceClassId: String,
        profileId: String,
        sensorMetadata: SensorMetadata,
        imuWindow: List<Triple<Float, Float, Float>>,
        modelVersions: Map<String, String>
    ): Pair<String, ByteArray> {
        val location = geoManager.getCurrentLocation()
        val seal = CaptureSeal(
            nonce = nonce,
            frameHash = frameHash,
            evidenceHash = evidenceHash,
            integrityToken = integrityToken,
            geohash6 = location?.let { geoManager.getGeohash6(it) } ?: "unknown",
            geoConfidence = geoManager.getGeoConfidence(location),
            timestamp = System.currentTimeMillis(),
            deviceClassId = deviceClassId,
            profileId = profileId,
            keyTier = attestationManager.getKeyTier().name,
            sensorMetadata = sensorMetadata,
            imuWindow = imuWindow,
            modelVersions = modelVersions
        )

        val sealJson = Json.encodeToString(CaptureSeal.serializer(), seal)
        val signature = try {
            signData(sealJson.toByteArray())
        } catch (e: Exception) {
            ByteArray(0)
        }

        return sealJson to signature
    }

    private fun signData(data: ByteArray): ByteArray {
        val privateKey = keyStore.getKey("pricelens_identity_key", null) as java.security.PrivateKey
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(privateKey)
            update(data)
            sign()
        }
    }
}
