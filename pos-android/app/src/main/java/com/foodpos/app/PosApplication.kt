package com.foodpos.app

import android.app.Application
import com.foodpos.app.alerts.AlertNotifier

class PosApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AlertNotifier.createChannel(this)
        AlertNotifier.schedule(this)
        // Upload anything left from the last session and start the 15-minute background sync.
        container.sync.schedulePeriodic()
        container.sync.requestSync()
    }
}
