package com.pricelens.domain.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReputationTest {

    @Test
    fun `reputation should be slow to earn`() {
        val newAccount = Reputation(0.0, 0.0)
        val score0 = newAccount.score()
        
        val after1Success = newAccount.update(true).score()
        val after10Successes = (1..10).fold(newAccount) { acc, _ -> acc.update(true) }.score()
        
        // Even with 10 successes, it should be high but not 1.0 immediately
        assertTrue(after10Successes > score0)
        assertTrue(after10Successes < 0.9f)
    }

    @Test
    fun `reputation should be fast to lose`() {
        val establishedAccount = (1..10).fold(Reputation(0.0, 0.0)) { acc, _ -> acc.update(true) }
        val highScale = establishedAccount.score()
        
        // One disagreement should significantly drop score (weighted 2.5x)
        val after1Failure = establishedAccount.update(false).score()
        
        assertTrue(after1Failure < highScale)
        assertTrue(highScale - after1Failure > 0.1f)
    }

    @Test
    fun `established account should outrank lucky new account`() {
        // 2/2 lucky streak
        val luckyNew = (1..2).fold(Reputation(0.0, 0.0)) { acc, _ -> acc.update(true) }.score()
        
        // 90/100 solid history
        val established = (1..100).fold(Reputation(0.0, 0.0)) { acc, i -> acc.update(i <= 90) }.score()
        
        // P10 of Beta(91, 11) should be higher than P10 of Beta(3, 1)
        assertTrue("Established ($established) should beat lucky new ($luckyNew)", established > luckyNew)
    }
}
