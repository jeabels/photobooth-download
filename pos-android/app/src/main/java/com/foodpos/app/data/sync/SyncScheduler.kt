package com.foodpos.app.data.sync

/** Repositories call this after every local write; the sync runs in the background when online. */
fun interface SyncScheduler {
    fun requestSync()
}
