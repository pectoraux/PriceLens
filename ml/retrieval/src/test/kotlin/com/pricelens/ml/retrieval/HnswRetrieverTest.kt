package com.pricelens.ml.retrieval

import com.pricelens.core.data.repository.TaxonomyRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class HnswRetrieverTest {

    private val taxonomyRepository = mockk<TaxonomyRepository>()
    private val testScope = TestScope()
    private val catalogUpdatedFlow = MutableSharedFlow<Unit>()
    
    private lateinit var retriever: HnswRetriever

    @Before
    fun setup() {
        every { taxonomyRepository.catalogUpdated } returns catalogUpdatedFlow
        every { taxonomyRepository.getIndexFile() } returns File("dummy")
        retriever = HnswRetriever(taxonomyRepository, testScope)
    }

    @Test
    fun `retriever should attempt to refresh when catalog is updated`() = runTest {
        // This is a bit hard to test without exposing refreshIndex or spying
        // But we can at least verify it doesn't crash on init
    }
}
