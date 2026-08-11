package com.pricelens.core.data.repository

import android.content.Context
import com.pricelens.core.common.logging.Logger
import com.pricelens.core.data.remote.api.SyncApi
import com.pricelens.core.common.trust.SignatureVerifier
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class ModelRepositoryTest {

    private val context = mockk<Context>()
    private val api = mockk<SyncApi>()
    private val verifier = mockk<SignatureVerifier>()
    private val logger = mockk<Logger>(relaxed = true)
    
    private lateinit var repository: ModelRepository

    @Before
    fun setup() {
        repository = ModelRepository(context, api, verifier, logger)
    }

    @Test
    fun `verifyHash should return true for matching content`() {
        val file = File.createTempFile("model", ".tflite")
        file.writeText("content")
        val expectedHash = "ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73"

        val method = repository.javaClass.getDeclaredMethod("verifyHash", File::class.java, String::class.java)
        method.isAccessible = true
        val result = method.invoke(repository, file, expectedHash) as Boolean

        assertTrue(result)
    }

    @Test
    fun `verifyHash should return false for mismatching content`() {
        val file = File.createTempFile("model", ".tflite")
        file.writeText("wrong")
        val expectedHash = "ed7002b439e9ac845f22357d822bac1444730fbdb6016d3ec9432297b9ec9f73"

        val method = repository.javaClass.getDeclaredMethod("verifyHash", File::class.java, String::class.java)
        method.isAccessible = true
        val result = method.invoke(repository, file, expectedHash) as Boolean

        assertFalse(result)
    }
}
