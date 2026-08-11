package com.pricelens.core.data.repository

import com.pricelens.core.attest.AttestationManager
import android.graphics.Bitmap
import com.pricelens.core.data.local.db.dao.ObservationDao
import com.pricelens.core.data.local.db.entity.ObservationEntity
import com.pricelens.core.data.local.file.EvidenceStorage
import com.pricelens.core.data.remote.api.CaptureApi
import com.pricelens.core.data.remote.model.ObservationSubmission
import com.pricelens.domain.trust.ConsensusPolicy
import com.pricelens.domain.trust.ConsensusStatus
import com.pricelens.domain.price.PriceObservation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ObservationRepository @Inject constructor(
    private val observationDao: ObservationDao,
    private val evidenceStorage: EvidenceStorage,
    private val attestationManager: AttestationManager,
    private val api: CaptureApi
) {
    val allObservations: Flow<List<ObservationEntity>> = observationDao.getAllObservations()

    fun getHistory(): Flow<List<ObservationEntity>> {
        return allObservations.map { list ->
            list.sortedByDescending { it.timestamp }
        }
    }

    fun getObservation(id: String): Flow<ObservationEntity?> {
        return kotlinx.coroutines.flow.flow {
            emit(observationDao.getObservationById(id))
        }
    }

    fun observeObservation(id: String): Flow<ObservationEntity?> {
        return observationDao.observeObservationById(id)
    }

    suspend fun saveDraft(observation: ObservationEntity) {
        observationDao.insertObservation(observation.copy(syncStatus = "DRAFT"))
    }

    fun saveEvidence(id: String, bitmap: Bitmap) {
        evidenceStorage.saveEvidence(id, bitmap)
    }

    suspend fun submitObservation(id: String) {
        val observation = observationDao.getObservationById(id) ?: return
        observationDao.insertObservation(observation.copy(syncStatus = "PENDING"))
        // Sync is handled by WorkManager
    }

    suspend fun updateItem(observationId: String, itemSlug: String) {
        val observation = observationDao.getObservationById(observationId) ?: return
        observationDao.insertObservation(
            observation.copy(
                itemId = itemSlug,
                labelWasCorrected = itemSlug != observation.predictedItemId
            )
        )
    }

    suspend fun updateQuantityAndUnit(observationId: String, quantity: Double, unit: String) {
        val observation = observationDao.getObservationById(observationId) ?: return
        observationDao.insertObservation(
            observation.copy(
                quantity = quantity,
                unit = unit,
                priceWasCorrected = true // Adjusting quantity affects per-unit price
            )
        )
    }

    /**
     * Simulates local consensus evaluation based on cached observations (H-10).
     */
    suspend fun evaluateLocalConsensus(observationId: String) {
        val obs = observationDao.getObservationById(observationId) ?: return
        val all = observationDao.getPendingObservations() // Simplified: search all local pending/synced
        
        val candidate = PriceObservation(
            contributorId = "self",
            minorUnits = obs.priceMinor,
            weight = 1.0f // Initial weight
        )
        
        val peers = all.filter { it.id != observationId && it.itemId == obs.itemId }.map {
            PriceObservation(
                contributorId = "peer_${it.id.take(4)}",
                minorUnits = it.priceMinor,
                weight = 0.8f
            )
        }

        val result = ConsensusPolicy.evaluateConsensus(
            candidate = candidate,
            peers = peers,
            hasStandardAttestation = obs.signature != null
        )

        if (result.status != ConsensusStatus.PENDING) {
            observationDao.insertObservation(obs.copy(consensusStatus = result.status.name))
        }
    }

    suspend fun syncPendingObservations() {
        val pending = observationDao.getPendingObservations()
        pending.forEach { obs ->
            try {
                val submission = ObservationSubmission(
                    clientId = UUID.fromString(obs.id),
                    nonce = "fake_nonce",
                    itemSlug = obs.itemId ?: "unknown",
                    priceMinor = obs.priceMinor,
                    currencyCode = obs.currencyCode,
                    quantity = obs.quantity,
                    unit = obs.unit,
                    geohash6 = obs.geohash,
                    geoConfidence = 1.0, // TODO: Get real confidence
                    captureSeal = obs.captureSealJson ?: "",
                    attestationChain = attestationManager.getAttestationChain(),
                    deviceClassId = "pixel_8",
                    modelVersions = emptyMap<String, String>(),
                    predictedItemSlug = obs.predictedItemId,
                    predictedConfidence = (obs.predictedConfidence ?: 0f).toDouble(),
                    labelWasCorrected = obs.labelWasCorrected,
                    priceWasCorrected = obs.priceWasCorrected,
                    abstained = obs.abstained
                )

                val response = api.submitObservation("fake_token", "fake_sig", submission)
                if (response.body().status == "accepted") {
                    observationDao.insertObservation(obs.copy(syncStatus = "SYNCED"))
                }
            } catch (e: Exception) {
                observationDao.insertObservation(obs.copy(syncStatus = "FAILED", lastError = e.message))
                // Let WorkManager retry
            }
        }
    }
}
