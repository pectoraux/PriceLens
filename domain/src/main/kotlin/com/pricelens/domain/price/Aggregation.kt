package com.pricelens.domain.price

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToLong

data class PriceObservation(
    val contributorId: String,
    val minorUnits: Long,
    val weight: Float
)

data class PriceBand(
    val p10: Long,
    val p50: Long,
    val p90: Long
)

/**
 * Robust price aggregation using weighted medians and MAD rejection (H-09).
 */
object Aggregation {

    /**
     * Aggregates a list of price observations into a robust price band.
     */
    fun aggregate(
        observations: List<PriceObservation>,
        priorP50: Long? = null
    ): PriceBand? {
        if (observations.isEmpty()) return null

        // 1. Contributor Influence Cap (15% per cell)
        val contributorWeights = observations.groupBy { it.contributorId }
            .mapValues { (_, obs) -> obs.sumOf { it.weight.toDouble() } }
        
        val totalRawWeight = contributorWeights.values.sum()
        val maxWeightPerContributor = totalRawWeight * 0.15
        
        val cappedObservations = observations.map { obs ->
            val totalForThisUser = contributorWeights[obs.contributorId] ?: 0.0
            if (totalForThisUser > maxWeightPerContributor) {
                // Scale down this observation's weight proportionally
                val scale = maxWeightPerContributor / totalForThisUser
                obs.copy(weight = (obs.weight * scale).toFloat())
            } else {
                obs
            }
        }

        // 2. Work in log-space for prices
        val logPrices = cappedObservations.map { ln(it.minorUnits.toDouble()) to it.weight }
        
        // 3. Outlier rejection by MAD (3.5x threshold)
        val initialMedian = weightedMedian(logPrices)
        val absoluteDeviations = logPrices.map { (logP, w) -> abs(logP - initialMedian) to w }
        val mad = weightedMedian(absoluteDeviations)
        
        val filteredLogPrices = if (mad > 0) {
            logPrices.filter { (logP, _) -> abs(logP - initialMedian) <= 3.5 * mad }
        } else {
            logPrices
        }

        if (filteredLogPrices.isEmpty()) return null

        // 4. Final quantiles
        val p10Log = weightedQuantile(filteredLogPrices, 0.1)
        val p50Log = weightedQuantile(filteredLogPrices, 0.5)
        val p90Log = weightedQuantile(filteredLogPrices, 0.9)

        // 5. Convert back to minor units
        return PriceBand(
            p10 = exp(p10Log).roundToLong(),
            p50 = exp(p50Log).roundToLong(),
            p90 = exp(p90Log).roundToLong()
        )
    }

    private fun weightedMedian(data: List<Pair<Double, Float>>): Double {
        return weightedQuantile(data, 0.5)
    }

    private fun weightedQuantile(data: List<Pair<Double, Float>>, quantile: Double): Double {
        if (data.isEmpty()) return 0.0
        val sorted = data.sortedBy { it.first }
        val totalWeight = sorted.sumOf { it.second.toDouble() }
        val targetWeight = totalWeight * quantile
        
        var cumulativeWeight = 0.0
        for ((value, weight) in sorted) {
            cumulativeWeight += weight
            if (cumulativeWeight >= targetWeight) return value
        }
        return sorted.last().first
    }
}
