package com.pricelens.domain.trust

import com.pricelens.domain.policy.Thresholds
import com.pricelens.domain.price.PriceObservation
import kotlin.math.abs
import kotlin.math.ln

enum class ConsensusStatus {
    PENDING,
    CONFIRMED,
    DISPUTED,
    OUTLIER
}

data class ConsensusResult(
    val status: ConsensusStatus,
    val combinedWeight: Float,
    val contributorCount: Int
)

object ConsensusPolicy {

    /**
     * Evaluates if a candidate observation reaches consensus based on its peers (H-10).
     */
    fun evaluateConsensus(
        candidate: PriceObservation,
        peers: List<PriceObservation>,
        hasStandardAttestation: Boolean
    ): ConsensusResult {
        // Filter peers by log-price agreement (within ± 25%)
        val logCandidate = ln(candidate.minorUnits.toDouble())
        val agreeingPeers = peers.filter { peer ->
            val logPeer = ln(peer.minorUnits.toDouble())
            abs(logCandidate - logPeer) <= Thresholds.CONSENSUS_PRICE_LOG_TOLERANCE
        }

        val independentContributors = (agreeingPeers + candidate)
            .map { it.contributorId }
            .distinct()
        
        val combinedWeight = (agreeingPeers + candidate).sumOf { it.weight.toDouble() }.toFloat()

        val meetsConditions = independentContributors.size >= Thresholds.CONSENSUS_MIN_CONTRIBUTORS &&
                combinedWeight >= Thresholds.CONSENSUS_MIN_COMBINED_WEIGHT &&
                hasStandardAttestation

        return ConsensusResult(
            status = if (meetsConditions) ConsensusStatus.CONFIRMED else ConsensusStatus.PENDING,
            combinedWeight = combinedWeight,
            contributorCount = independentContributors.size
        )
    }
}
