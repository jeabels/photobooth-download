@file:OptIn(ExperimentalMaterial3Api::class)

package com.foodpos.app.ui.pos

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foodpos.app.alerts.ProductStock
import com.foodpos.app.alerts.StockLevel
import com.foodpos.app.core.Money
import com.foodpos.app.ui.ShowMessages
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.components.EmptyState
import com.foodpos.app.ui.theme.LocalStatusColors

@Composable
fun PosScreen() {
    val vm = appViewModel { PosViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val checkout by vm.checkout.collectAsStateWithLifecycle()
    val form by vm.form.collectAsStateWithLifecycle()
    val printing by vm.printing.collectAsStateWithLifecycle()
    var cartSheet by rememberSaveable { mutableStateOf(false) }
    val symbol = settings.currencySymbol
    ShowMessages(vm)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Wide screens (tablets) show the cart beside the products; phones use a bottom bar + sheet.
        val sideBySide = maxWidth >= 720.dp
        if (sideBySide) {
            Row(Modifier.fillMaxSize()) {
                Catalog(state, symbol, vm, Modifier.weight(1f))
                VerticalDivider()
                CartPanel(
                    state = state,
                    symbol = symbol,
                    onAdd = vm::add,
                    onSubtract = vm::subtract,
                    onRemove = vm::remove,
                    onClear = vm::clearCart,
                    onCheckout = vm::openCheckout,
                    modifier = Modifier.width(360.dp).fillMaxHeight(),
                    fillHeight = true,
                )
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Catalog(state, symbol, vm, Modifier.weight(1f))
                if (state.cart.isNotEmpty()) {
                    CartBar(state.itemCount, Money.format(state.subtotalCents, symbol), onClick = { cartSheet = true })
                }
            }
        }
    }

    if (cartSheet && state.cart.isNotEmpty()) {
        ModalBottomSheet(
            onDismissRequest = { cartSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            CartPanel(
                state = state,
                symbol = symbol,
                onAdd = vm::add,
                onSubtract = vm::subtract,
                onRemove = vm::remove,
                onClear = { vm.clearCart(); cartSheet = false },
                onCheckout = { cartSheet = false; vm.openCheckout() },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            )
        }
    }

    when (val status = checkout) {
        CheckoutStatus.Idle -> Unit
        CheckoutStatus.Editing, CheckoutStatus.Saving -> CheckoutDialog(
            subtotalCents = state.subtotalCents,
            itemCount = state.itemCount,
            form = form,
            settings = settings,
            saving = status == CheckoutStatus.Saving,
            onEdit = vm::editForm,
            onDismiss = vm::closeCheckout,
            onConfirm = vm::placeOrder,
        )
        is CheckoutStatus.Done -> OrderCompleteDialog(
            order = status.order,
            receipt = status.receipt,
            settings = settings,
            printing = printing,
            onPrint = { vm.print(status.order) },
            onDone = vm::finishOrder,
        )
    }
}

@Composable
private fun Catalog(state: PosUiState, symbol: String, vm: PosViewModel, modifier: Modifier) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Search products") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear search") }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = vm::toggleView) {
                Icon(
                    if (state.gridView) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (state.gridView) "Show as list" else "Show as grid",
                )
            }
        }
        if (state.categories.size > 1) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            ) {
                item {
                    FilterChip(selected = state.category == null, onClick = { vm.setCategory(null) }, label = { Text("All") })
                }
                items(state.categories) { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { vm.setCategory(if (state.category == category) null else category) },
                        label = { Text(category) },
                    )
                }
            }
        }

        when {
            state.loading -> Unit
            state.products.isEmpty() -> EmptyState(
                icon = Icons.Filled.Inventory2,
                title = if (state.query.isBlank() && state.category == null) "No products yet" else "No matches",
                message = if (state.query.isBlank() && state.category == null) {
                    "Add products and receive stock in Inventory to start selling."
                } else {
                    "Try a different search or category."
                },
            )
            state.gridView -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.products, key = { it.product.id }) { item ->
                    ProductCard(item, state.inCart[item.product.id] ?: 0, symbol, onClick = { vm.add(item) })
                }
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.products, key = { it.product.id }) { item ->
                    ProductRow(
                        item = item,
                        inCart = state.inCart[item.product.id] ?: 0,
                        symbol = symbol,
                        onAdd = { vm.add(item) },
                        onSubtract = { vm.subtract(item.product.id) },
                    )
                }
            }
        }
    }
}

/** Stock left after what is already in the cart, with the colour of its level. */
@Composable
private fun StockCounter(item: ProductStock, inCart: Int) {
    val colors = LocalStatusColors.current
    val left = (item.sellable - inCart).coerceAtLeast(0)
    val color = when {
        left == 0 -> colors.danger
        left <= item.product.lowStockThreshold || item.level == StockLevel.RUNNING_LOW -> colors.warning
        else -> colors.good
    }
    Text(
        if (left == 0) "Out of stock" else "$left ${item.product.unit} left",
        style = MaterialTheme.typography.labelMedium,
        color = color,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ProductCard(item: ProductStock, inCart: Int, symbol: String, onClick: () -> Unit) {
    val available = item.sellable - inCart > 0
    Card(
        onClick = onClick,
        enabled = available,
        colors = CardDefaults.cardColors(
            containerColor = if (inCart > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        modifier = Modifier.fillMaxWidth().height(132.dp).alpha(if (available || inCart > 0) 1f else 0.55f),
    ) {
        Box(Modifier.fillMaxSize().padding(12.dp)) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    item.product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.product.category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Spacer(Modifier.weight(1f))
                Text(Money.format(item.product.priceCents, symbol), style = MaterialTheme.typography.titleMedium)
                StockCounter(item, inCart)
            }
            if (inCart > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    Text(
                        "×$inCart",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProductRow(item: ProductStock, inCart: Int, symbol: String, onAdd: () -> Unit, onSubtract: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (inCart > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.product.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Money.format(item.product.priceCents, symbol), style = MaterialTheme.typography.bodyMedium)
                    StockCounter(item, inCart)
                }
            }
            QuantityStepper(inCart, onSubtract = onSubtract, onAdd = onAdd, canAdd = item.sellable - inCart > 0)
        }
    }
}

@Composable
private fun QuantityStepper(quantity: Int, onSubtract: () -> Unit, onAdd: () -> Unit, canAdd: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FilledTonalIconButton(onClick = onSubtract, enabled = quantity > 0, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Remove, contentDescription = "Remove one")
        }
        Text(
            quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.widthIn(min = 32.dp).padding(horizontal = 4.dp),
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        FilledTonalIconButton(onClick = onAdd, enabled = canAdd, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Add, contentDescription = "Add one")
        }
    }
}

@Composable
private fun CartBar(count: Int, total: String, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.ShoppingCart, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text("$count item${if (count == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text("View cart · $total", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun CartPanel(
    state: PosUiState,
    symbol: String,
    onAdd: (ProductStock) -> Unit,
    onSubtract: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
    /** True in the tablet side panel (totals pinned to the bottom), false in the phone sheet. */
    fillHeight: Boolean = false,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Current order", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (state.cart.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }
        if (state.cart.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.RemoveShoppingCart,
                title = "Cart is empty",
                message = "Tap products to add them.",
                modifier = Modifier.weight(1f, fill = fillHeight),
            )
        } else {
            LazyColumn(Modifier.weight(1f, fill = fillHeight), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(state.cart, key = { it.stock.product.id }) { line ->
                    val product = line.stock.product
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${Money.format(product.priceCents, symbol)} each · ${Money.format(line.lineTotalCents, symbol)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        QuantityStepper(
                            quantity = line.quantity,
                            onSubtract = { onSubtract(product.id) },
                            onAdd = { onAdd(line.stock) },
                            canAdd = line.stock.sellable > line.quantity,
                        )
                        IconButton(onClick = { onRemove(product.id) }) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Remove ${product.name}")
                        }
                    }
                }
            }
        }
        HorizontalDivider()
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Subtotal (${state.itemCount})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(Money.format(state.subtotalCents, symbol), style = MaterialTheme.typography.titleLarge)
            }
            Button(
                onClick = onCheckout,
                enabled = state.cart.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Checkout", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
