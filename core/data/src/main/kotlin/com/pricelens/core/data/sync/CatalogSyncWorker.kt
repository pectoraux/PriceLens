package com.pricelens.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pricelens.core.common.trust.SignatureVerifier
import com.pricelens.core.data.repository.TaxonomyRepository
import com.pricelens.core.data.remote.api.SyncApi
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

@HiltWorker
class CatalogSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val taxonomyRepository: TaxonomyRepository,
    private val syncApi: SyncApi,
    private val signatureVerifier: SignatureVerifier,
    private val httpClient: HttpClient
) : CoroutineWorker(context, params) {

    companion object {
        private const val PUBLIC_KEY = "PLACEHOLDER_PUBLIC_KEY"
    }

    override suspend fun doWork(): Result {
        return try {
            val locale = inputData.getString("locale") ?: "en-US"
            val geohash = inputData.getString("geohash") ?: ""
            
            val response = syncApi.getCatalogManifest(locale, geohash, null)
            val manifest = response.body()
            
            // TODO: Verify manifest signature if provided in header or body
            
            for (bundle in manifest.bundles) {
                when (bundle.kind) {
                    "taxonomy_and_prototypes" -> {
                        // This bundle contains both for atomicity across types
                        syncCatalogBundle(bundle.url, bundle.sha256, bundle.signature)
                    }
                    "retrieval_index" -> {
                        syncIndexBundle(bundle.url, bundle.sha256, bundle.signature)
                    }
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncCatalogBundle(url: String, expectedHash: String, signature: String) {
        val tempFile = File(applicationContext.cacheDir, "catalog_bundle.json")
        downloadFile(url, tempFile)
        
        if (verifyIntegrity(tempFile, expectedHash, signature)) {
            val bundle: com.pricelens.core.data.model.CatalogBundle = httpClient.get(url).body()
            taxonomyRepository.updateCatalogAtomic(bundle)
        }
        tempFile.delete()
    }

    private suspend fun syncIndexBundle(url: String, expectedHash: String, signature: String) {
        val indexFile = taxonomyRepository.getIndexFile()
        val tempFile = File(applicationContext.cacheDir, "index_bundle.hnsw")
        downloadFile(url, tempFile)
        
        if (verifyIntegrity(tempFile, expectedHash, signature)) {
            tempFile.renameTo(indexFile)
            taxonomyRepository.notifyIndexUpdated()
        } else {
            tempFile.delete()
        }
    }

    private suspend fun downloadFile(url: String, dest: File) {
        val response = httpClient.get(url)
        val bytes = response.body<ByteArray>()
        FileOutputStream(dest).use { it.write(bytes) }
    }

    private fun verifyIntegrity(file: File, expectedHash: String, signature: String): Boolean {
        // 1. SHA-256
        val digest = MessageDigest.getInstance("SHA-256")
        val actualHash = file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        
        if (!actualHash.equals(expectedHash, ignoreCase = true)) return false
        
        // 2. Ed25519 Signature
        return signatureVerifier.verifyFile(file, signature, PUBLIC_KEY)
    }
}
