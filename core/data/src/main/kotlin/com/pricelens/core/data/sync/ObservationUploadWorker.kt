package com.pricelens.core.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pricelens.core.data.repository.ObservationRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class ObservationUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val observationRepository: ObservationRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            observationRepository.syncPendingObservations()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}
