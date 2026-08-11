package com.pricelens.feature.review

import androidx.lifecycle.SavedStateHandle
import com.pricelens.core.data.repository.ObservationRepository
import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.core.geo.GeoManager
import com.pricelens.domain.policy.Thresholds
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ReviewViewModelTest {

    private val observationRepository = mockk<ObservationRepository>(relaxed = true)
    private val taxonomyRepository = mockk<TaxonomyRepository>()
    private val priceRepository = mockk<com.pricelens.core.data.repository.PriceRepository>()
    private val geoManager = mockk<GeoManager>()
    private val savedStateHandle = SavedStateHandle(mapOf("observationId" to "test_id"))
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: ReviewViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        every { observationRepository.getObservation("test_id") } returns flowOf(null)
        viewModel = ReviewViewModel(savedStateHandle, observationRepository, taxonomyRepository, priceRepository, geoManager)
    }

    @Test
    fun `isPlausible should return false for extreme quantities`() = runTest {
        viewModel.onUnitChanged("kg")
        viewModel.onQuantityChanged((Thresholds.MAX_PLAUSIBLE_KG + 10).toString())
        assertFalse(viewModel.isPlausible.value)

        viewModel.onUnitChanged("piece")
        viewModel.onQuantityChanged((Thresholds.MAX_PLAUSIBLE_PIECES + 10).toString())
        assertFalse(viewModel.isPlausible.value)
    }

    @Test
    fun `isPlausible should return true for reasonable quantities`() = runTest {
        viewModel.onUnitChanged("kg")
        viewModel.onQuantityChanged("1.5")
        assertTrue(viewModel.isPlausible.value)

        viewModel.onUnitChanged("piece")
        viewModel.onQuantityChanged("12")
        assertTrue(viewModel.isPlausible.value)
    }
}
