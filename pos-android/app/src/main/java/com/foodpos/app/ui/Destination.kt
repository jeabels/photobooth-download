package com.foodpos.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/** Every screen in the drawer. Owner-only screens are hidden from staff and not even registered. */
enum class Destination(val route: String, val label: String, val icon: ImageVector, val ownerOnly: Boolean) {
    POS("pos", "Point of Sale", Icons.Filled.PointOfSale, ownerOnly = false),
    ORDERS("orders", "Orders", Icons.AutoMirrored.Filled.ReceiptLong, ownerOnly = false),
    INVENTORY("inventory", "Inventory", Icons.Filled.Inventory2, ownerOnly = false),
    ALERTS("alerts", "Alerts", Icons.Filled.NotificationsActive, ownerOnly = false),
    LEDGER("ledger", "Stock Ledger", Icons.AutoMirrored.Filled.ListAlt, ownerOnly = true),
    DASHBOARD("dashboard", "Dashboard", Icons.Filled.Insights, ownerOnly = true),
    EXPENSES("expenses", "Expenses", Icons.Filled.Payments, ownerOnly = true),
    USERS("users", "Team", Icons.Filled.Group, ownerOnly = true),
    SETTINGS("settings", "Settings", Icons.Filled.Settings, ownerOnly = true),
}
