package com.pricelens.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `Money addition should be accurate`() {
        val m1 = Money(100, "USD")
        val m2 = Money(200, "USD")
        val result = m1 + m2
        assertEquals(300L, result.minorUnits)
    }

    @Test
    fun `Money formatting should handle decimals correctly`() {
        val m = Money(12345, "USD")
        assertEquals("$123.45", m.format("$"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `Money addition should throw for different currencies`() {
        val m1 = Money(100, "USD")
        val m2 = Money(200, "EUR")
        m1 + m2
    }
}
