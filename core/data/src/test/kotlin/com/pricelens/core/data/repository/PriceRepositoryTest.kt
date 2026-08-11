package com.pricelens.core.data.repository

import com.pricelens.core.data.local.db.dao.PriceCellDao
import com.pricelens.core.data.local.db.entity.PriceCellCacheEntity
import com.pricelens.core.data.remote.api.PriceApi
import com.pricelens.core.data.remote.model.PriceBand
import com.pricelens.core.data.remote.model.PriceEstimateRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.openapitools.client.infrastructure.HttpResponse

class PriceRepositoryTest {

    private val priceCellDao = mockk<PriceCellDao>(relaxed = true)
    private val priceApi = mockk<PriceApi>()
    private lateinit var repository: PriceRepository

    @Before
    fun setup() {
        repository = PriceRepository(priceCellDao, priceApi)
    }

    @Test
    fun `getPriceEstimate should return cached value if fresh`() = runTest {
        val cached = PriceCellCacheEntity(
            key = "geo|slug|kg",
            p10Minor = 100,
            p50Minor = 150,
            p90Minor = 200,
            currencyCode = "USD",
            nObservations = 10,
            nContributors = 5,
            freshnessDays = 1,
            confidence = "HIGH",
            computedAtMs = System.currentTimeMillis()
        )
        coEvery { priceCellDao.getPriceCell("geo|slug|kg") } returns cached

        val result = repository.getPriceEstimate("geo", "slug", "kg", 1.0)

        assertNotNull(result)
        assertEquals(150L, result?.p50Minor)
        coVerify(exactly = 0) { priceApi.estimatePrice(any()) }
    }

    @Test
    fun `getPriceEstimate should fetch from API if cache is stale`() = runTest {
        val stale = PriceCellCacheEntity(
            key = "geo|slug|kg",
            p10Minor = 100,
            p50Minor = 150,
            p90Minor = 200,
            currencyCode = "USD",
            nObservations = 10,
            nContributors = 5,
            freshnessDays = 30,
            confidence = "HIGH",
            computedAtMs = System.currentTimeMillis() - (48 * 60 * 60 * 1000L) // 48h
        )
        val freshBand = mockk<PriceBand> {
            every { p10Minor } returns 110
            every { p50Minor } returns 160
            every { p90Minor } returns 210
            every { currencyCode } returns "USD"
            every { unit } returns "kg"
            every { nObservations } returns 12
            every { nContributors } returns 6
            every { confidence } returns PriceBand.Confidence.HIGH
            every { freshnessDays } returns 0
        }
        
        val freshResponse = mockk<HttpResponse<PriceBand>>()
        coEvery { freshResponse.body() } returns freshBand
        
        coEvery { priceCellDao.getPriceCell("geo|slug|kg") } returns stale
        coEvery { priceApi.estimatePrice(any()) } returns freshResponse

        val result = repository.getPriceEstimate("geo", "slug", "kg", 1.0)

        assertNotNull(result)
        assertEquals(160L, result?.p50Minor)
        coVerify(exactly = 1) { priceApi.estimatePrice(any()) }
    }
}
