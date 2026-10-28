package com.foodpos.app.data.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.foodpos.app.PosApplication

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val sync = (applicationContext as PosApplication).container.sync
        if (!sync.isConfigured) return Result.success()
        return if (sync.syncNow().isSuccess) {
            Result.success()
        } else if (runAttemptCount < MAX_ATTEMPTS) {
            Result.retry()
        } else {
            Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 5
    }
}
