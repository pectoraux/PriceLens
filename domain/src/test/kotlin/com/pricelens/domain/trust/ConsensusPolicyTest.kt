package com.pricelens.domain.trust

import com.pricelens.domain.price.PriceObservation
import org.junit.Assert.assertEquals
import org.junit.Test

class ConsensusPolicyTest {

    @Test
    fun `evaluateConsensus should confirm when conditions are met`() {
        val candidate = PriceObservation("u1", 100, 1.0f)
        val peers = listOf(
            PriceObservation("u2", 110, 1.0f), // Agree
            PriceObservation("u3", 95, 1.0f)   // Agree
        )
        
        val result = ConsensusPolicy.evaluateConsensus(
            candidate, peers, hasStandardAttestation = true
        )
        
        assertEquals(ConsensusStatus.CONFIRMED, result.status)
        assertEquals(3.0f, result.combinedWeight, 0.1f)
    }

    @Test
    fun `evaluateConsensus should remain pending if weight is low`() {
        val candidate = PriceObservation("u1", 100, 0.5f)
        val peers = listOf(
            PriceObservation("u2", 100, 0.5f)
        )
        
        val result = ConsensusPolicy.evaluateConsensus(
            candidate, peers, hasStandardAttestation = true
        )
        
        assertEquals(ConsensusStatus.PENDING, result.status) // Weight 1.0 < 2.0
    }

    @Test
    fun `evaluateConsensus should remain pending if no standard attestation`() {
        val candidate = PriceObservation("u1", 100, 1.0f)
        val peers = listOf(
            PriceObservation("u2", 100, 1.0f),
            PriceObservation("u3", 100, 1.0f)
        )
        
        val result = ConsensusPolicy.evaluateConsensus(
            candidate, peers, hasStandardAttestation = false
        )
        
        assertEquals(ConsensusStatus.PENDING, result.status)
    }
}
