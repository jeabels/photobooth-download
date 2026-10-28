package com.foodpos.app.ui.orders

import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.core.Dates
import com.foodpos.app.data.local.OrderStatus
import com.foodpos.app.data.local.OrderWithItems
import com.foodpos.app.printer.Receipt
import com.foodpos.app.ui.BaseViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class OrderRange(val label: String) { TODAY("Today"), WEEK("Last 7 days"), MONTH("Last 30 days"), ALL("All") }

data class OrdersUiState(
    val range: OrderRange = OrderRange.TODAY,
    val query: String = "",
    val orders: List<OrderWithItems> = emptyList(),
) {
    private val completed get() = orders.filter { it.order.status == OrderStatus.COMPLETED }
    val salesCents: Long get() = completed.sumOf { it.order.totalCents }
    val completedCount: Int get() = completed.size
}

@OptIn(ExperimentalCoroutinesApi::class)
class OrdersViewModel(private val container: AppContainer) : BaseViewModel() {
    private val range = MutableStateFlow(OrderRange.TODAY)
    private val query = MutableStateFlow("")
    val settings = container.settings.settings

    val state: StateFlow<OrdersUiState> = combine(
        range.flatMapLatest { container.orders.observeSince(startOf(it)) },
        range,
        query,
    ) { orders, r, q ->
        val filtered = if (q.isBlank()) {
            orders
        } else {
            orders.filter { o ->
                o.order.queueNumber.contains(q, true) || o.order.customerName.contains(q, true) ||
                    o.items.any { it.productName.contains(q, true) }
            }
        }
        OrdersUiState(r, q, filtered)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OrdersUiState())

    private val _receipt = MutableStateFlow<Receipt?>(null)
    val receipt: StateFlow<Receipt?> = _receipt.asStateFlow()

    private val _printing = MutableStateFlow(false)
    val printing: StateFlow<Boolean> = _printing.asStateFlow()

    fun setRange(value: OrderRange) { range.value = value }
    fun setQuery(value: String) { query.value = value }

    fun loadReceipt(order: OrderWithItems?) {
        _receipt.value = null
        if (order == null) return
        viewModelScope.launch { _receipt.value = container.receipts.receiptFor(order) }
    }

    fun reprint(order: OrderWithItems) {
        if (_printing.value) return
        _printing.value = true
        viewModelScope.launch {
            runCatching { container.receipts.print(order) }.report("Receipt printed")
            _printing.value = false
        }
    }

    fun voidOrder(order: OrderWithItems, reason: String) {
        val user = container.session.currentUser ?: return
        viewModelScope.launch {
            container.orders.voidOrder(order.order.id, user, reason).report("Order ${order.order.queueNumber} voided, stock returned")
        }
    }

    private fun startOf(range: OrderRange): Long {
        val today = Dates.today()
        return when (range) {
            OrderRange.TODAY -> Dates.startOfDay(today)
            OrderRange.WEEK -> Dates.startOfDay(today.minusDays(6))
            OrderRange.MONTH -> Dates.startOfDay(today.minusDays(29))
            OrderRange.ALL -> 0L
        }
    }
}
