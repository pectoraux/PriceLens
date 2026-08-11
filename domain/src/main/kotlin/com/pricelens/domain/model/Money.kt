package com.pricelens.domain.model

/**
 * Represents a monetary value.
 * Money is always handled in minor units (e.g., cents) as Long to avoid floating point errors.
 */
data class Money(
    val minorUnits: Long,
    val currencyCode: String
) {
    operator fun plus(other: Money): Money {
        require(currencyCode == other.currencyCode) { "Cannot add different currencies: $currencyCode and ${other.currencyCode}" }
        return Money(minorUnits + other.minorUnits, currencyCode)
    }

    operator fun minus(other: Money): Money {
        require(currencyCode == other.currencyCode) { "Cannot subtract different currencies: $currencyCode and ${other.currencyCode}" }
        return Money(minorUnits - other.minorUnits, currencyCode)
    }

    operator fun times(factor: Double): Money {
        return Money((minorUnits * factor).toLong(), currencyCode)
    }

    fun format(symbol: String = "$"): String {
        return "$symbol${String.format("%.2f", minorUnits / 100.0)}"
    }

    companion object {
        fun zero(currencyCode: String) = Money(0, currencyCode)
    }
}
