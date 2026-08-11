package com.pricelens.ml.fusion

import com.pricelens.ml.pipeline.contract.Fuser
import com.pricelens.ml.pipeline.contract.Retriever
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LateFuserTest {

    private lateinit var fuser: LateFuser

    @Before
    fun setup() {
        fuser = LateFuser()
    }

    @Test
    fun `fuse should prioritize OCR matches`() = runTest {
        val visual = listOf(Retriever.Candidate("apple", 0.9f))
        val ocr = mapOf("banana" to 1.0f) // Strong OCR match
        val priors = emptyMap<String, Float>()

        val results = fuser.fuse(visual, ocr, priors)

        assertTrue(results.isNotEmpty())
        assertEquals("banana", results[0].label)
        assertTrue(results[0].confidence > 0.9f)
    }

    @Test
    fun `fuse should handle empty signals gracefully`() = runTest {
        val results = fuser.fuse(emptyList(), emptyMap(), emptyMap())
        assertTrue(results.isEmpty())
    }
}
