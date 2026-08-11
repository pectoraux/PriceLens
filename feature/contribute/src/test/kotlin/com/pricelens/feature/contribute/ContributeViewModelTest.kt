package com.pricelens.feature.contribute

import com.pricelens.core.data.repository.ObservationRepository
import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.core.geo.GeoManager
import com.pricelens.core.data.local.db.entity.TaxonomyItemCacheEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ContributeViewModelTest {

    private val observationRepository = mockk<ObservationRepository>(relaxed = true)
    private val taxonomyRepository = mockk<TaxonomyRepository>()
    private val geoManager = mockk<GeoManager>()
    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: ContributeViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        coEvery { geoManager.getCurrentLocation() } returns null
        viewModel = ContributeViewModel(observationRepository, taxonomyRepository, geoManager)
    }

    @Test
    fun `submitContribution should save and submit observation`() = runTest {
        val item = TaxonomyItemCacheEntity(
            slug = "tomato",
            displayName = "Tomato",
            vernacularNames = "",
            category = "produce",
            defaultUnit = "kg",
            densityKgPerL = 1.0,
            shapeModel = "spheroid",
            textEmbedding = ByteArray(0)
        )
        viewModel.onItemSelected(item)
        
        viewModel.submitContribution(price = 15000, quantity = 2.0, unit = "kg")
        
        coVerify { observationRepository.saveDraft(any()) }
        coVerify { observationRepository.submitObservation(any()) }
        assertEquals(ContributeUiState.Success, viewModel.uiState.value)
    }
}
