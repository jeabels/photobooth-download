package com.foodpos.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.alerts.InventoryAlert
import com.foodpos.app.data.local.UserStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide state for the navigation shell: alert and approval badges, sync status, new-alert popups. */
class ShellViewModel(private val container: AppContainer) : ViewModel() {
    val alerts: StateFlow<List<InventoryAlert>> = container.inventory.observeAlerts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pendingApprovals: StateFlow<Int> = container.users.observeAll()
        .map { users -> users.count { it.status == UserStatus.PENDING } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val syncRunning = container.sync.running
    val settings = container.settings.settings
    val syncConfigured: Boolean get() = container.sync.isConfigured

    private val _newAlerts = Channel<List<InventoryAlert>>(Channel.CONFLATED)
    /** Alerts that appeared since the last emission, for the "pushed to the UI" snackbar. */
    val newAlerts = _newAlerts.receiveAsFlow()

    private var seenKeys: Set<String>? = null

    init {
        viewModelScope.launch {
            alerts.collect { list ->
                val keys = list.map { it.key }.toSet()
                val previous = seenKeys
                if (previous != null) {
                    val fresh = list.filter { it.key !in previous }
                    if (fresh.isNotEmpty()) _newAlerts.trySend(fresh)
                }
                seenKeys = keys
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch { container.sync.syncNow() }
    }

    fun logout() = container.auth.logout()
}
