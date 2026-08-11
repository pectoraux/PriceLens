package com.pricelens.core.common.trust

import java.io.File
import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class SignatureVerifier {

    /**
     * Verifies an Ed25519 signature of a file.
     * publicKeyBase64: The base64-encoded public key.
     */
    fun verifyFile(file: File, signatureBase64: String, publicKeyBase64: String): Boolean {
        return try {
            val publicKey = decodePublicKey(publicKeyBase64)
            val signatureBytes = Base64.getDecoder().decode(signatureBase64)
            
            val sig = Signature.getInstance("Ed25519")
            sig.initVerify(publicKey)
            
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    sig.update(buffer, 0, bytesRead)
                }
            }
            
            sig.verify(signatureBytes)
        } catch (e: Exception) {
            false
        }
    }

    private fun decodePublicKey(base64: String): PublicKey {
        val keyBytes = Base64.getDecoder().decode(base64)
        val spec = X509EncodedKeySpec(keyBytes)
        val kf = KeyFactory.getInstance("Ed25519")
        return kf.generatePublic(spec)
    }
}
