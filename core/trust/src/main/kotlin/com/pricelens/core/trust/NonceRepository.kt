package com.pricelens.core.trust

import com.pricelens.core.data.remote.api.CaptureApi
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NonceRepository @Inject constructor(
    private val api: CaptureApi
) {
    private var cachedNonce: String? = null
    private var lastFetchTime = 0L

    companion object {
        private const val NONCE_TTL_MS = 10 * 60 * 1000L // 10 minutes
    }

    suspend fun fetchNonce(): String {
        if (cachedNonce != null && (System.currentTimeMillis() - lastFetchTime) < NONCE_TTL_MS) {
            return cachedNonce!!
        }

        return try {
            val nonce = api.getNonce().body().nonce ?: ""
            cachedNonce = nonce
            lastFetchTime = System.currentTimeMillis()
            nonce
        } catch (e: Exception) {
            // Fallback for offline mode if necessary, or throw
            throw e
        }
    }
}
