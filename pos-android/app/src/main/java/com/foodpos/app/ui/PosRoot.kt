@file:OptIn(ExperimentalMaterial3Api::class)

package com.foodpos.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.foodpos.app.core.Dates
import com.foodpos.app.data.local.Role
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.security.SessionState
import com.foodpos.app.ui.alerts.AlertsScreen
import com.foodpos.app.ui.auth.AuthScreen
import com.foodpos.app.ui.dashboard.DashboardScreen
import com.foodpos.app.ui.expenses.ExpensesScreen
import com.foodpos.app.ui.inventory.InventoryScreen
import com.foodpos.app.ui.inventory.LedgerScreen
import com.foodpos.app.ui.orders.OrdersScreen
import com.foodpos.app.ui.pos.PosScreen
import com.foodpos.app.ui.settings.SettingsScreen
import com.foodpos.app.ui.users.UsersScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun PosRoot(widthSizeClass: WindowWidthSizeClass, openAlertsRequest: MutableStateFlow<Boolean>) {
    val container = LocalAppContainer.current
    val session by container.session.state.collectAsStateWithLifecycle()
    when (val state = session) {
        SessionState.Loading -> Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        SessionState.SignedOut -> AuthScreen()
        is SessionState.SignedIn -> key(state.user.id) {
            MainShell(state.user, widthSizeClass, openAlertsRequest)
        }
    }
}

/**
 * Adaptive navigation: phones and small tablets get a sliding drawer behind a menu button; large
 * tablets (expanded width) get a permanent drawer side by side with the content.
 */
@Composable
private fun MainShell(
    user: UserEntity,
    widthSizeClass: WindowWidthSizeClass,
    openAlertsRequest: MutableStateFlow<Boolean>,
) {
    val shell = appViewModel { ShellViewModel(it) }
    val isOwner = user.role == Role.OWNER
    val destinations = remember(isOwner) { Destination.entries.filter { isOwner || !it.ownerOnly } }
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val current = destinations.firstOrNull { it.route == backStack?.destination?.route } ?: Destination.POS

    val alerts by shell.alerts.collectAsStateWithLifecycle()
    val pendingApprovals by shell.pendingApprovals.collectAsStateWithLifecycle()
    val syncRunning by shell.syncRunning.collectAsStateWithLifecycle()
    val settings by shell.settings.collectAsStateWithLifecycle()
    val openAlerts by openAlertsRequest.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val permanent = widthSizeClass == WindowWidthSizeClass.Expanded

    fun navigate(destination: Destination) {
        navController.navigate(destination.route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        scope.launch { drawerState.close() }
    }

    // New inventory alerts are pushed to the UI as a snackbar wherever the user is.
    LaunchedEffect(shell) {
        shell.newAlerts.collect { fresh ->
            val first = fresh.first()
            val message = if (fresh.size == 1) "${first.type.label}: ${first.productName}" else "${fresh.size} new inventory alerts"
            val result = snackbar.showSnackbar(message, actionLabel = "View", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) navigate(Destination.ALERTS)
        }
    }
    LaunchedEffect(openAlerts) {
        if (openAlerts) {
            navigate(Destination.ALERTS)
            openAlertsRequest.value = false
        }
    }

    val drawer: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 16.dp),
        ) {
            Text(
                settings.businessName,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                "${user.fullName} · ${if (isOwner) "Owner" else "Staff"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 16.dp),
            )
            destinations.forEach { destination ->
                val count = when (destination) {
                    Destination.ALERTS -> alerts.size
                    Destination.USERS -> pendingApprovals
                    else -> 0
                }
                NavigationDrawerItem(
                    label = { Text(destination.label) },
                    icon = { Icon(destination.icon, contentDescription = null) },
                    selected = destination == current,
                    onClick = { navigate(destination) },
                    badge = if (count > 0) {
                        { Badge { Text(count.toString()) } }
                    } else {
                        null
                    },
                    modifier = Modifier.padding(vertical = 2.dp),
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            NavigationDrawerItem(
                label = { Text("Sign out") },
                icon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
                selected = false,
                onClick = shell::logout,
            )
        }
    }

    val content: @Composable (showMenuButton: Boolean) -> Unit = { showMenuButton ->
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(current.label) },
                    navigationIcon = {
                        if (showMenuButton) {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Filled.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    actions = {
                        SyncButton(
                            configured = shell.syncConfigured,
                            running = syncRunning,
                            error = settings.lastSyncError,
                            lastSyncAt = settings.lastSyncAt,
                            onSync = shell::syncNow,
                            onExplain = { scope.launch { snackbar.showSnackbar(it) } },
                        )
                        IconButton(onClick = { navigate(Destination.ALERTS) }) {
                            BadgedBox(badge = { if (alerts.isNotEmpty()) Badge { Text(alerts.size.toString()) } }) {
                                Icon(Icons.Filled.Notifications, contentDescription = "Inventory alerts")
                            }
                        }
                    },
                )
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            CompositionLocalProvider(LocalSnackbar provides snackbar) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.POS.route,
                    modifier = Modifier.padding(padding).consumeWindowInsets(padding),
                ) {
                    composable(Destination.POS.route) { PosScreen() }
                    composable(Destination.ORDERS.route) { OrdersScreen(isOwner) }
                    composable(Destination.INVENTORY.route) { InventoryScreen(isOwner) }
                    composable(Destination.ALERTS.route) { AlertsScreen(isOwner) { navigate(Destination.INVENTORY) } }
                    if (isOwner) {
                        composable(Destination.LEDGER.route) { LedgerScreen() }
                        composable(Destination.DASHBOARD.route) { DashboardScreen() }
                        composable(Destination.EXPENSES.route) { ExpensesScreen() }
                        composable(Destination.USERS.route) { UsersScreen(currentUserId = user.id) }
                        composable(Destination.SETTINGS.route) { SettingsScreen() }
                    }
                }
            }
        }
    }

    if (permanent) {
        PermanentNavigationDrawer(
            drawerContent = { PermanentDrawerSheet(Modifier.width(280.dp)) { drawer() } },
        ) { content(false) }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = { ModalDrawerSheet { drawer() } },
        ) { content(true) }
    }
}

@Composable
private fun SyncButton(
    configured: Boolean,
    running: Boolean,
    error: String?,
    lastSyncAt: Long,
    onSync: () -> Unit,
    onExplain: (String) -> Unit,
) {
    when {
        !configured -> IconButton(onClick = { onExplain("Offline mode: cloud sync is not set up in this build") }) {
            Icon(Icons.Filled.CloudOff, contentDescription = "Offline mode")
        }
        running -> Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        error != null -> IconButton(onClick = {
            onExplain("Will sync when online. Last error: $error")
            onSync()
        }) {
            Icon(Icons.Filled.SyncProblem, contentDescription = "Sync pending", tint = MaterialTheme.colorScheme.error)
        }
        else -> IconButton(onClick = {
            onExplain(if (lastSyncAt > 0) "Synced ${Dates.formatDateTime(lastSyncAt)}" else "Syncing…")
            onSync()
        }) {
            Icon(Icons.Filled.CloudDone, contentDescription = "Synced")
        }
    }
}
