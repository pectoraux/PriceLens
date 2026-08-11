package com.pricelens.core.common.trust

import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import java.io.File

class SignatureVerifierTest {

    private lateinit var verifier: SignatureVerifier

    @Before
    fun setup() {
        verifier = SignatureVerifier()
    }

    @Test
    fun `verifyFile should return false for invalid public key`() {
        val file = File.createTempFile("test", ".txt")
        val result = verifier.verifyFile(file, "sig", "invalid_key")
        assertFalse(result)
    }

    @Test
    fun `verifyFile should return false if file does not exist`() {
        val file = File("non_existent")
        val result = verifier.verifyFile(file, "sig", "key")
        assertFalse(result)
    }
}
