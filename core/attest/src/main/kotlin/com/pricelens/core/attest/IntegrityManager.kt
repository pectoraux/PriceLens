package com.pricelens.core.attest

import android.content.Context
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import com.pricelens.core.common.logging.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IntegrityManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: Logger
) {
    private val integrityManager = IntegrityManagerFactory.create(context)
    private var cachedToken: String? = null
    private var lastFetchTime = 0L

    companion object {
        private const val CLOUD_PROJECT_NUMBER = 123456789L // From environment config
        private const val TOKEN_TTL_MS = 3 * 60 * 1000L // 3 minutes
    }

    /**
     * Requests a Play Integrity token bound to the provided nonce.
     */
    suspend fun requestIntegrityToken(nonce: String): String? {
        if (cachedToken != null && (System.currentTimeMillis() - lastFetchTime) < TOKEN_TTL_MS) {
            return cachedToken
        }

        return try {
            val request = IntegrityTokenRequest.builder()
                .setCloudProjectNumber(CLOUD_PROJECT_NUMBER)
                .setNonce(nonce)
                .build()

            val response = integrityManager.requestIntegrityToken(request).await()
            val token = response.token()
            cachedToken = token
            lastFetchTime = System.currentTimeMillis()
            token
        } catch (e: Exception) {
            logger.e("IntegrityManager", "Failed to get integrity token", e)
            null
        }
    }
}
