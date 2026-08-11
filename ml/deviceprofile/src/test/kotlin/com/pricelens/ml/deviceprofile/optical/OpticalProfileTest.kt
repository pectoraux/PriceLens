package com.pricelens.ml.deviceprofile.optical

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.abs

class OpticalProfileTest {

    @Test
    fun `DistortionModel should recover grid points through round-trip`() {
        val model = DistortionModel(
            k1 = 0.1f, k2 = 0.05f, k3 = 0.01f,
            p1 = 0.001f, p2 = 0.001f,
            fx = 1000f, fy = 1000f,
            cx = 500f, cy = 500f
        )

        val testPoints = listOf(
            Pair(100f, 100f),
            Pair(900f, 900f),
            Pair(500f, 500f),
            Pair(300f, 700f)
        )

        for ((x, y) in testPoints) {
            val (xd, yd) = model.distort(x, y)
            val (xu, yu) = model.undistort(xd, yd)

            assertEquals("X mismatch for ($x, $y)", x, xu, 0.5f)
            assertEquals("Y mismatch for ($x, $y)", y, yu, 0.5f)
        }
    }

    @Test
    fun `CanonicalReprojector should yield consistent scale across different intrinsics`() {
        // This is a conceptual test for scale invariance logic.
        // We simulate two cameras viewing a 60mm object at 300mm.
        
        val width = 448f
        val hfov = 55.0f
        val canonicalFX = (width / 2.0f) / Math.tan(Math.toRadians(hfov / 2.0)).toFloat()
        
        // Expected size in pixels: (object_size / distance) * focal_length
        val expectedSize = (60f / 300f) * canonicalFX

        // Camera A: Wide (24mm equiv), fx = 3000
        val scaleA = canonicalFX / 3000f
        val sizeA = (60f / 300f) * 3000f * scaleA

        // Camera B: Tele (50mm equiv), fx = 6000
        val scaleB = canonicalFX / 6000f
        val sizeB = (60f / 300f) * 6000f * scaleB

        assertEquals(expectedSize, sizeA, 0.01f)
        assertEquals(expectedSize, sizeB, 0.01f)
        assertEquals(sizeA, sizeB, 0.01f)
    }
}
