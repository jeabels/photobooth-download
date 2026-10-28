package com.foodpos.app.ui.inventory

import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.alerts.BatchStatus
import com.foodpos.app.alerts.ProductStock
import com.foodpos.app.alerts.StockLevel
import com.foodpos.app.data.local.MovementRow
import com.foodpos.app.data.local.MovementType
import com.foodpos.app.data.repository.DeliveryDraft
import com.foodpos.app.data.repository.ProductDraft
import com.foodpos.app.ui.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class StockFilter(val label: String) {
    ALL("All"), LOW("Running low"), OUT("Out of stock"), EXPIRING("Spoiling / spoiled"),
}

data class InventoryUiState(
    val loading: Boolean = true,
    val items: List<ProductStock> = emptyList(),
    val allProducts: List<ProductStock> = emptyList(),
    val categories: List<String> = emptyList(),
    val query: String = "",
    val filter: StockFilter = StockFilter.ALL,
)

class InventoryViewModel(private val container: AppContainer) : BaseViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(StockFilter.ALL)
    val settings = container.settings.settings

    val state: StateFlow<InventoryUiState> = combine(container.inventory.observeStock(), query, filter) { stock, q, f ->
        val items = stock.filter { item ->
            val matches = q.isBlank() || item.product.name.contains(q, true) || item.product.sku.contains(q, true) ||
                item.batches.any { it.batch.batchNumber.contains(q, true) }
            val inFilter = when (f) {
                StockFilter.ALL -> true
                StockFilter.LOW -> item.level == StockLevel.RUNNING_LOW
                StockFilter.OUT -> item.level == StockLevel.OUT_OF_STOCK
                StockFilter.EXPIRING -> item.batches.any { it.status == BatchStatus.SPOILED || it.status == BatchStatus.SPOILING_SOON }
            }
            matches && inFilter
        }
        InventoryUiState(false, items, stock, stock.map { it.product.category }.distinct().sorted(), q, f)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InventoryUiState())

    fun setQuery(value: String) { query.value = value }
    fun setFilter(value: StockFilter) { filter.value = value }

    fun suggestBatchNumber() = container.inventory.suggestBatchNumber()

    fun saveProduct(draft: ProductDraft, onDone: () -> Unit) = viewModelScope.launch {
        if (container.inventory.saveProduct(draft).report("Product saved")) onDone()
    }

    fun archiveProduct(productId: String) = viewModelScope.launch {
        container.inventory.archiveProduct(productId).report("Product archived")
    }

    fun receiveDelivery(draft: DeliveryDraft, onDone: () -> Unit) = viewModelScope.launch {
        val user = container.session.currentUser ?: return@launch
        if (container.inventory.receiveDelivery(draft, user).report("Delivery recorded")) onDone()
    }

    fun adjust(batchId: String, delta: Int, reason: String, onDone: () -> Unit) = viewModelScope.launch {
        val user = container.session.currentUser ?: return@launch
        if (container.inventory.adjustStock(batchId, delta, reason, user).report("Stock adjusted")) onDone()
    }

    fun writeOff(batchId: String) = viewModelScope.launch {
        val user = container.session.currentUser ?: return@launch
        container.inventory.writeOffBatch(batchId, user).report("Spoiled stock written off")
    }
}

data class LedgerUiState(
    val rows: List<MovementRow> = emptyList(),
    val type: MovementType? = null,
    val query: String = "",
)

class LedgerViewModel(container: AppContainer) : BaseViewModel() {
    private val type = MutableStateFlow<MovementType?>(null)
    private val query = MutableStateFlow("")

    val state: StateFlow<LedgerUiState> = combine(container.inventory.observeLedger(1_000), type, query) { rows, t, q ->
        LedgerUiState(
            rows = rows.filter { row ->
                (t == null || row.movement.type == t) &&
                    (q.isBlank() || row.productName.orEmpty().contains(q, true) || row.batchNumber.orEmpty().contains(q, true) ||
                        row.movement.note.contains(q, true) || row.movement.userName.contains(q, true))
            },
            type = t,
            query = q,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LedgerUiState())

    fun setType(value: MovementType?) { type.value = value }
    fun setQuery(value: String) { query.value = value }
}
