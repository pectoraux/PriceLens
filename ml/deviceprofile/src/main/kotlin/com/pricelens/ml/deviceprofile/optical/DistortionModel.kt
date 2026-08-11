package com.pricelens.ml.deviceprofile.optical

import kotlin.math.pow

/**
 * Implements the Brown–Conrady distortion model (radial + tangential).
 */
class DistortionModel(
    private val k1: Float,
    private val k2: Float,
    private val k3: Float,
    private val p1: Float,
    private val p2: Float,
    private val fx: Float,
    private val fy: Float,
    private val cx: Float,
    private val cy: Float
) {
    /**
     * Maps a point (x, y) in canonical space to distorted sensor coordinates.
     */
    fun distort(x: Float, y: Float): Pair<Float, Float> {
        val nx = (x - cx) / fx
        val ny = (y - cy) / fy
        val r2 = nx * nx + ny * ny
        val r4 = r2 * r2
        val r6 = r2 * r4

        val radial = 1 + k1 * r2 + k2 * r4 + k3 * r6
        val tangentialX = 2 * p1 * nx * ny + p2 * (r2 + 2 * nx * nx)
        val tangentialY = p1 * (r2 + 2 * ny * ny) + 2 * p2 * nx * ny

        val dx = nx * radial + tangentialX
        val dy = ny * radial + tangentialY

        return Pair(dx * fx + cx, dy * fy + cy)
    }

    /**
     * Inverse mapping (undistort) using Newton-Raphson iterative solver with full Jacobian.
     */
    fun undistort(xd: Float, yd: Float, maxIter: Int = 10): Pair<Float, Float> {
        val nxd = (xd - cx) / fx
        val nyd = (yd - cy) / fy
        
        var nx = nxd
        var ny = nyd

        for (i in 0 until maxIter) {
            val r2 = nx * nx + ny * ny
            val r4 = r2 * r2
            val r6 = r2 * r4

            val radial = 1 + k1 * r2 + k2 * r4 + k3 * r6
            
            val dx_radial = nx * radial
            val dy_radial = ny * radial
            
            val dx_tangential = 2 * p1 * nx * ny + p2 * (r2 + 2 * nx * nx)
            val dy_tangential = p1 * (r2 + 2 * ny * ny) + 2 * p2 * nx * ny
            
            val fx_val = dx_radial + dx_tangential - nxd
            val fy_val = dy_radial + dy_tangential - nyd
            
            // Numerical Jacobian estimation
            val eps = 1e-4f
            val r2_ex = (nx + eps).pow(2) + ny.pow(2)
            val rad_ex = 1 + k1 * r2_ex + k2 * r2_ex.pow(2) + k3 * r2_ex.pow(3)
            val dfx_dnx = ((nx + eps) * rad_ex + 2 * p1 * (nx + eps) * ny + p2 * (r2_ex + 2 * (nx + eps).pow(2)) - (dx_radial + dx_tangential)) / eps
            
            val r2_ey = nx.pow(2) + (ny + eps).pow(2)
            val rad_ey = 1 + k1 * r2_ey + k2 * r2_ey.pow(2) + k3 * r2_ey.pow(3)
            val dfy_dny = ((ny + eps) * rad_ey + p1 * (r2_ey + 2 * (ny + eps).pow(2)) + 2 * p2 * nx * (ny + eps) - (dy_radial + dy_tangential)) / eps

            nx -= fx_val / dfx_dnx
            ny -= fy_val / dfy_dny

            if (fx_val * fx_val + fy_val * fy_val < 1e-10) break
        }

        return Pair(nx * fx + cx, ny * fy + cy)
    }
}
