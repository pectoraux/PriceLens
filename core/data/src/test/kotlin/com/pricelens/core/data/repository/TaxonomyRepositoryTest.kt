package com.pricelens.core.data.repository

import android.content.Context
import com.pricelens.core.data.local.db.dao.GtinMapDao
import com.pricelens.core.data.local.db.dao.PrototypeDao
import com.pricelens.core.data.local.db.dao.TaxonomyDao
import io.ktor.client.HttpClient
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class TaxonomyRepositoryTest {

    private val context = mockk<Context>()
    private val taxonomyDao = mockk<TaxonomyDao>()
    private val prototypeDao = mockk<PrototypeDao>()
    private val gtinMapDao = mockk<GtinMapDao>()
    
    private lateinit var repository: TaxonomyRepository

    @Before
    fun setup() {
        every { taxonomyDao.getAllItems() } returns flowOf(emptyList())
        repository = TaxonomyRepository(context, taxonomyDao, prototypeDao, gtinMapDao)
    }

    @Test
    fun `searchItems should format FTS query correctly`() = runTest {
        every { taxonomyDao.searchItems("*apple*") } returns flowOf(emptyList())
        
        repository.searchItems("apple")
        
        verify { taxonomyDao.searchItems("*apple*") }
    }

    @Test
    fun `searchItems should return empty for blank query`() = runTest {
        every { taxonomyDao.searchItems("") } returns flowOf(emptyList())
        
        repository.searchItems(" ")
        
        verify { taxonomyDao.searchItems("") }
    }
}
