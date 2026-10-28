package com.foodpos.app.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.foodpos.app.data.local.PosDatabase
import com.foodpos.app.data.repository.SettingsRepository
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * Decides when to sync. Every local write asks for a sync; WorkManager runs it as soon as the device
 * has a network connection (now, or hours later), retries with backoff on failure, and also syncs
 * every 15 minutes to pick up changes made on other devices.
 *
 * When the app was built without google-services.json, Firebase is not configured and sync is off:
 * the app keeps working 100% locally.
 */
class SyncManager(
    private val context: Context,
    private val db: PosDatabase,
    private val settings: SettingsRepository,
) : SyncScheduler {
    private val mutex = Mutex()
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    val isConfigured: Boolean get() = FirebaseApp.getApps(context).isNotEmpty()

    private val onlineOnly = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun requestSync() {
        if (!isConfigured) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(onlineOnly)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedulePeriodic() {
        if (!isConfigured) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(onlineOnly)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Runs one push-then-pull pass. Safe to call from the UI ("Sync now") and from the worker. */
    suspend fun syncNow(): Result<Unit> {
        if (!isConfigured) return Result.failure(IllegalStateException("Cloud sync is not set up in this build"))
        return mutex.withLock {
            _running.value = true
            val result = runCatching { FirestoreSync(db, settings).run() }
            _running.value = false
            settings.update {
                if (result.isSuccess) {
                    it.copy(lastSyncAt = System.currentTimeMillis(), lastSyncError = null)
                } else {
                    it.copy(lastSyncError = result.exceptionOrNull()?.message ?: "Sync failed")
                }
            }
            result
        }
    }

    private companion object {
        const val WORK_NOW = "sync-now"
        const val WORK_PERIODIC = "sync-periodic"
    }
}
