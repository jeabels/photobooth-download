package com.foodpos.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.CompositionLocalProvider
import com.foodpos.app.ui.LocalAppContainer
import com.foodpos.app.ui.PosRoot
import com.foodpos.app.ui.theme.PosTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Set when the app is opened from an inventory alert notification. */
    private val openAlerts = MutableStateFlow(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openAlerts.value = intent?.getBooleanExtra(EXTRA_OPEN_ALERTS, false) == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && savedInstanceState == null) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        val container = (application as PosApplication).container
        setContent {
            val windowSize = calculateWindowSizeClass(this)
            CompositionLocalProvider(LocalAppContainer provides container) {
                PosTheme {
                    PosRoot(widthSizeClass = windowSize.widthSizeClass, openAlertsRequest = openAlerts)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_ALERTS, false)) openAlerts.value = true
    }

    companion object {
        const val EXTRA_OPEN_ALERTS = "open_alerts"
    }
}
