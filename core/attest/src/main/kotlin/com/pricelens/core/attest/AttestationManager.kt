package com.pricelens.core.attest

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.pricelens.core.common.logging.Logger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

enum class KeyTier {
    STRONGBOX,
    TEE,
    SOFTWARE,
    NONE
}

@Singleton
class AttestationManager @Inject constructor(
    private val logger: Logger
) {
    companion object {
        private const val KEY_ALIAS = "pricelens_identity_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
    }

    private val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }

    fun getKeyTier(): KeyTier {
        if (!keyStore.containsAlias(KEY_ALIAS)) return KeyTier.NONE
        val entry = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return KeyTier.NONE
        val description = entry.privateKey.toString()
        
        return when {
            description.contains("StrongBox", ignoreCase = true) -> KeyTier.STRONGBOX
            description.contains("Keymaster", ignoreCase = true) || description.contains("TEE", ignoreCase = true) -> KeyTier.TEE
            else -> KeyTier.SOFTWARE
        }
    }

    /**
     * Ensures an attested identity key exists.
     */
    fun ensureIdentityKey(challenge: ByteArray? = null) {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            generateIdentityKey(challenge ?: "pricelens_init".toByteArray())
        }
    }

    private fun generateIdentityKey(challenge: ByteArray) {
        try {
            val keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_EC,
                KEYSTORE_PROVIDER
            )

            val builder = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            ).apply {
                setDigests(KeyProperties.DIGEST_SHA256)
                setUserAuthenticationRequired(false)
                
                setAttestationChallenge(challenge)

                if (Build.VERSION.SDK_INT >= 31) {
                    // StrongBox is available from API 28
                    try {
                        setIsStrongBoxBacked(true)
                    } catch (e: Exception) {
                        logger.w("AttestationManager", "StrongBox not supported, falling back to TEE")
                    }
                }
            }

            keyPairGenerator.initialize(builder.build())
            keyPairGenerator.generateKeyPair()
            logger.i("AttestationManager", "Identity key generated successfully")
        } catch (e: Exception) {
            logger.e("AttestationManager", "Failed to generate identity key", e)
        }
    }

    fun getAttestationChain(): List<String> {
        val certificates = keyStore.getCertificateChain(KEY_ALIAS)
        return certificates?.map { 
            android.util.Base64.encodeToString(it.encoded, android.util.Base64.NO_WRAP) 
        } ?: emptyList()
    }

    fun getPublicKeyBase64(): String? {
        val certificate = keyStore.getCertificate(KEY_ALIAS) ?: return null
        return android.util.Base64.encodeToString(certificate.publicKey.encoded, android.util.Base64.NO_WRAP)
    }
}
