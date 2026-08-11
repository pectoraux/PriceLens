package com.pricelens.ml.portion

import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.core.geo.DistanceEstimator
import com.pricelens.ml.pipeline.contract.PortionEstimator
import com.pricelens.ml.pipeline.model.CanonicalFrame
import com.pricelens.ml.pipeline.model.Detection
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.tan

@Singleton
class DefaultPortionEstimator @Inject constructor(
    private val distanceEstimator: DistanceEstimator,
    private val taxonomyRepository: TaxonomyRepository,
    private val massEstimator: MassEstimator
) : PortionEstimator {

    override suspend fun estimate(
        frame: CanonicalFrame,
        detection: Detection,
        distanceMm: Float
    ): PortionEstimator.PortionResult {
        // 1. Get refined distance from metadata (G-01)
        val distResult = distanceEstimator.estimateDistance(frame.metadata)
        
        // 2. Physical Scale Calculation (G-03)
        // mm_per_px = (2 * d * tan(HFOV/2)) / ImageWidth
        val hFovRad = Math.toRadians(CanonicalFrame.HFOV_DEG.toDouble()).toFloat()
        val mmPerPx = (2 * distResult.distanceMm * tan(hFovRad / 2)) / CanonicalFrame.WIDTH
        
        // 3. Projected Area Calculation
        val rect = detection.boundingBox
        val areaPx2 = rect.width() * rect.height()
        val areaMm2 = areaPx2 * mmPerPx * mmPerPx

        // 4. Fetch Taxonomy Data
        val itemSlug = detection.label ?: return PortionEstimator.PortionResult(null, 0f)
        val taxonomyItem = taxonomyRepository.getItemBySlug(itemSlug)
        
        val density = (taxonomyItem?.densityKgPerL ?: 1.0).toFloat()
        // Approximate shape factor based on model type (G-02)
        val shapeFactor = when (taxonomyItem?.shapeModel) {
            "spheroid" -> 0.52f
            "cylinder" -> 0.78f
            "pile" -> 0.35f // Includes occlusion correction
            else -> 0.5f
        }

        // 5. Final Mass Estimation (G-04)
        val result = massEstimator.estimateMass(
            projectedAreaMm2 = areaMm2,
            shapeFactor = shapeFactor,
            densityKgL = density,
            distanceMm = distResult.distanceMm,
            distanceUncertaintyMm = distResult.uncertaintyMm
        )

        return PortionEstimator.PortionResult(
            massGrams = if (result.isConfident) result.massGrams else null,
            confidence = if (result.isConfident) 0.9f else 0.2f
        )
    }
}
