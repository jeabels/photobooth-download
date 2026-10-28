package com.foodpos.app.ui.pos

import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.alerts.ProductStock
import com.foodpos.app.data.local.OrderType
import com.foodpos.app.data.local.OrderWithItems
import com.foodpos.app.data.local.PaymentMethod
import com.foodpos.app.data.repository.CheckoutException
import com.foodpos.app.data.repository.CheckoutRequest
import com.foodpos.app.printer.Receipt
import com.foodpos.app.ui.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CartLine(val stock: ProductStock, val quantity: Int) {
    val lineTotalCents: Long get() = stock.product.priceCents * quantity
}

data class PosUiState(
    val loading: Boolean = true,
    val products: List<ProductStock> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
    val category: String? = null,
    val gridView: Boolean = true,
    val cart: List<CartLine> = emptyList(),
    /** productId to quantity already in the cart, for the live "left" counters. */
    val inCart: Map<String, Int> = emptyMap(),
) {
    val subtotalCents: Long get() = cart.sumOf { it.lineTotalCents }
    val itemCount: Int get() = cart.sumOf { it.quantity }
}

/** Checkout form, kept in the ViewModel so it survives rotation. */
data class CheckoutForm(
    val orderType: OrderType = OrderType.WALK_IN,
    val deliveryFee: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerAddress: String = "",
    val notes: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val cashReceived: String = "",
    val reference: String = "",
)

sealed interface CheckoutStatus {
    data object Idle : CheckoutStatus
    data object Editing : CheckoutStatus
    data object Saving : CheckoutStatus
    data class Done(val order: OrderWithItems, val receipt: Receipt?) : CheckoutStatus
}

class PosViewModel(private val container: AppContainer) : BaseViewModel() {
    private val cart = MutableStateFlow<Map<String, Int>>(linkedMapOf())
    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<String?>(null)
    private val gridView = MutableStateFlow(true)

    val settings = container.settings.settings

    val state: StateFlow<PosUiState> = combine(
        container.inventory.observeStock(), cart, query, category, gridView,
    ) { stock, cartMap, q, cat, grid ->
        val byId = stock.associateBy { it.product.id }
        val filtered = stock.filter { item ->
            (cat == null || item.product.category == cat) &&
                (q.isBlank() || item.product.name.contains(q, true) || item.product.sku.contains(q, true))
        }
        PosUiState(
            loading = false,
            products = filtered,
            categories = stock.map { it.product.category }.distinct().sorted(),
            query = q,
            category = cat,
            gridView = grid,
            cart = cartMap.mapNotNull { (id, qty) -> byId[id]?.let { CartLine(it, qty) } },
            inCart = cartMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PosUiState())

    private val _form = MutableStateFlow(CheckoutForm())
    val form: StateFlow<CheckoutForm> = _form.asStateFlow()

    private val _checkout = MutableStateFlow<CheckoutStatus>(CheckoutStatus.Idle)
    val checkout: StateFlow<CheckoutStatus> = _checkout.asStateFlow()

    private val _printing = MutableStateFlow(false)
    val printing: StateFlow<Boolean> = _printing.asStateFlow()

    fun setQuery(value: String) { query.value = value }
    fun setCategory(value: String?) { category.value = value }
    fun toggleView() { gridView.update { !it } }

    /** Adds one, unless that would sell more than the sellable stock. */
    fun add(item: ProductStock) {
        val current = cart.value[item.product.id] ?: 0
        if (current + 1 > item.sellable) {
            say(if (item.sellable <= 0) "${item.product.name} is out of stock" else "Only ${item.sellable} ${item.product.unit} of ${item.product.name} in stock")
            return
        }
        cart.update { it + (item.product.id to current + 1) }
    }

    fun subtract(productId: String) {
        cart.update { map ->
            val next = (map[productId] ?: 0) - 1
            if (next <= 0) map - productId else map + (productId to next)
        }
    }

    fun remove(productId: String) = cart.update { it - productId }

    fun clearCart() = cart.update { linkedMapOf() }

    fun openCheckout() {
        if (cart.value.isEmpty()) return
        _checkout.value = CheckoutStatus.Editing
    }

    fun closeCheckout() {
        if (_checkout.value == CheckoutStatus.Editing) _checkout.value = CheckoutStatus.Idle
    }

    fun editForm(transform: (CheckoutForm) -> CheckoutForm) = _form.update(transform)

    fun placeOrder(deliveryFeeCents: Long, cashReceivedCents: Long?) {
        val user = container.session.currentUser ?: return
        val f = _form.value
        _checkout.value = CheckoutStatus.Saving
        viewModelScope.launch {
            try {
                val order = container.orders.checkout(
                    CheckoutRequest(
                        lines = cart.value,
                        orderType = f.orderType,
                        deliveryFeeCents = deliveryFeeCents,
                        customerName = f.customerName,
                        customerPhone = f.customerPhone,
                        customerAddress = f.customerAddress,
                        notes = f.notes,
                        paymentMethod = f.paymentMethod,
                        amountTenderedCents = cashReceivedCents,
                        paymentReference = f.reference,
                    ),
                    user,
                )
                cart.value = linkedMapOf()
                _form.value = CheckoutForm()
                val receipt = runCatching { container.receipts.receiptFor(order) }.getOrNull()
                _checkout.value = CheckoutStatus.Done(order, receipt)
                if (container.settings.current.autoPrint && container.receipts.hasPrinter) print(order)
            } catch (e: CheckoutException) {
                _checkout.value = CheckoutStatus.Editing
                say(e.message ?: "Could not place the order")
            }
        }
    }

    fun finishOrder() {
        _checkout.value = CheckoutStatus.Idle
    }

    fun print(order: OrderWithItems) {
        if (_printing.value) return
        _printing.value = true
        viewModelScope.launch {
            runCatching { container.receipts.print(order) }.report("Receipt printed")
            _printing.value = false
        }
    }
}
