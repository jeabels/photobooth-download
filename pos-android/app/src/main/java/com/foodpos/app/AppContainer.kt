package com.foodpos.app

import android.content.Context
import com.foodpos.app.data.local.PosDatabase
import com.foodpos.app.data.repository.AuthRepository
import com.foodpos.app.data.repository.ExpenseRepository
import com.foodpos.app.data.repository.InventoryRepository
import com.foodpos.app.data.repository.OrderRepository
import com.foodpos.app.data.repository.SettingsRepository
import com.foodpos.app.data.repository.UserRepository
import com.foodpos.app.data.sync.SyncManager
import com.foodpos.app.printer.BluetoothPrinterService
import com.foodpos.app.printer.ReceiptPrinter
import com.foodpos.app.security.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency injection: one instance of each repository and service for the whole app.
 * ViewModels get what they need from here (see ui/AppViewModel.kt).
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database = PosDatabase.build(context)
    val settings = SettingsRepository(context)
    val sync = SyncManager(context, database, settings)
    val session = SessionManager(context, database.userDao(), appScope)

    val auth = AuthRepository(database, session, sync)
    val users = UserRepository(database, sync)
    val inventory = InventoryRepository(database, settings, sync)
    val orders = OrderRepository(database, settings, sync)
    val expenses = ExpenseRepository(database, sync)

    val bluetooth = BluetoothPrinterService(context)
    val receipts = ReceiptPrinter(settings, bluetooth)
}
