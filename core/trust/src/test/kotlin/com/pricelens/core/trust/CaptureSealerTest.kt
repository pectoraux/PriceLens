package com.pricelens.core.trust

import com.pricelens.core.geo.GeoManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CaptureSealerTest {

    private val geoManager = mockk<GeoManager>()
    private lateinit var sealer: CaptureSealer
    private lateinit var keyStore: KeyStore

    @Before
    fun setup() {
        coEvery { geoManager.getCurrentLocation() } returns null
        every { geoManager.getGeohash6(any()) } returns "6g0p00"
        every { geoManager.getGeoConfidence(any()) } returns 1.0f
        
        keyStore = mockk<KeyStore>(relaxed = true)
        
        sealer = CaptureSealer(geoManager, keyStore)
    }

    @Test
    fun `seal should produce a JSON with required fields`() = runTest {
        val (json, sig) = sealer.seal("nonce", "hash", "token", "pixel_8")
        assertNotNull(json)
        assertTrue(json.contains("nonce"))
        assertTrue(json.contains("token"))
        assertTrue(json.contains("hash"))
    }
}
