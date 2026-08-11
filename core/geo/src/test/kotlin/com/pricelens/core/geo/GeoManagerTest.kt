package com.pricelens.core.geo

import android.location.Location
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GeoManagerTest {

    private lateinit var geoManager: GeoManager

    @Before
    fun setup() {
        geoManager = GeoManager(mockk(relaxed = true))
    }

    @Test
    fun `isTravelPlausible should reject teleportation`() {
        val lastLoc = mockk<Location> {
            every { latitude } returns 1.2921 // Nairobi
            every { longitude } returns 36.8219
        }
        val newLoc = mockk<Location> {
            every { latitude } returns 51.5074 // London
            every { longitude } returns 0.1278
        }
        
        // 5 mins later
        val lastTime = System.currentTimeMillis() - (5 * 60 * 1000)
        
        val score = geoManager.isTravelPlausible(newLoc, lastLoc, lastTime)
        assertEquals(0.0f, score, 0.01f)
    }

    @Test
    fun `isTravelPlausible should accept normal walking`() {
        val lastLoc = mockk<Location> {
            every { latitude } returns -1.2921
            every { longitude } returns 36.8219
        }
        val newLoc = mockk<Location> {
            every { latitude } returns -1.2922 // 10 meters away
            every { longitude } returns 36.8219
        }
        
        val lastTime = System.currentTimeMillis() - (60 * 1000)
        
        val score = geoManager.isTravelPlausible(newLoc, lastLoc, lastTime)
        assertEquals(1.0f, score, 0.01f)
    }
}
