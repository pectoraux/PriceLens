package com.pricelens.core.data.repository

import android.content.Context
import com.pricelens.core.common.logging.Logger
import com.pricelens.core.data.remote.api.SyncApi
import com.pricelens.core.data.remote.model.ModelDefinition
import com.pricelens.core.common.trust.SignatureVerifier
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ModelRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SyncApi,
    private val signatureVerifier: SignatureVerifier,
    private val logger: Logger
) {
    suspend fun fetchManifest(deviceClassId: String, abi: String): List<ModelDefinition> {
        return api.getModelManifest(deviceClassId, abi).body().models
    }

    fun getModelFile(name: String): File {
        return File(context.filesDir, "models/$name.tflite")
    }

    fun isModelDownloaded(name: String): Boolean {
        return getModelFile(name).exists()
    }

    suspend fun downloadModel(entry: ModelDefinition) {
        val file = getModelFile(entry.name)
        file.parentFile?.mkdirs()
        
        URL(entry.url).openStream().use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }
        
        // 1. Verify SHA-256
        if (!verifyHash(file, entry.sha256)) {
            logger.e("ModelRepository", "Hash mismatch for ${entry.name}. Deleting.")
            file.delete()
            return
        }

        // 2. Verify Ed25519 Signature
        val publicKey = "PLACEHOLDER_PUBLIC_KEY" // TODO: Root of trust
        if (!signatureVerifier.verifyFile(file, entry.signature, publicKey)) {
            logger.e("ModelRepository", "Signature verification failed for ${entry.name}. Deleting.")
            file.delete()
            return
        }
        
        logger.i("ModelRepository", "Model ${entry.name} verified and ready.")
    }

    private fun verifyHash(file: File, expectedHash: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
}
