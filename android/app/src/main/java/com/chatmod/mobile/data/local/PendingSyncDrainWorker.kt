package com.chatmod.mobile.data.local

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.chatmod.mobile.ChatModApplication
import java.util.concurrent.TimeUnit

class PendingSyncDrainWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? ChatModApplication ?: return Result.failure()
        return runCatching {
            app.cloudSyncQueue.drainNow()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        private const val UniqueWorkName = "chatmod_pending_sync_drain"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<PendingSyncDrainWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UniqueWorkName,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }
}
