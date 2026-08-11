package com.pricelens.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pricelens.core.data.repository.ModelRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val modelRepository: ModelRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val models = modelRepository.fetchManifest("pixel_8", "arm64-v8a")
            models.forEach { entry ->
                if (!modelRepository.isModelDownloaded(entry.name)) {
                    modelRepository.downloadModel(entry)
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
