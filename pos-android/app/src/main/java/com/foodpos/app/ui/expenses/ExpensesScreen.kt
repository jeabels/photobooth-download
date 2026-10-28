package com.foodpos.app.ui.expenses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.foodpos.app.AppContainer
import com.foodpos.app.core.Dates
import com.foodpos.app.core.Money
import com.foodpos.app.data.local.ExpenseEntity
import com.foodpos.app.data.repository.ExpenseDraft
import com.foodpos.app.data.repository.ExpenseRepository
import com.foodpos.app.ui.BaseViewModel
import com.foodpos.app.ui.ShowMessages
import com.foodpos.app.ui.appViewModel
import com.foodpos.app.ui.components.ConfirmDialog
import com.foodpos.app.ui.components.DateField
import com.foodpos.app.ui.components.EmptyState
import com.foodpos.app.ui.components.MoneyField
import com.foodpos.app.ui.components.SectionCard
import com.foodpos.app.ui.components.SuggestField
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ExpensesUiState(
    val month: YearMonth = YearMonth.now(),
    val expenses: List<ExpenseEntity> = emptyList(),
    val categories: List<String> = ExpenseRepository.DEFAULT_CATEGORIES,
) {
    val totalCents: Long get() = expenses.sumOf { it.amountCents }
}

class ExpensesViewModel(private val container: AppContainer) : BaseViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    val settings = container.settings.settings

    val state: StateFlow<ExpensesUiState> = combine(container.expenses.observeAll(), month) { all, m ->
        val from = m.atDay(1).toEpochDay()
        val to = m.atEndOfMonth().toEpochDay()
        ExpensesUiState(
            month = m,
            expenses = all.filter { it.spentOn in from..to },
            categories = (ExpenseRepository.DEFAULT_CATEGORIES + all.map { it.category }).distinct(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState())

    fun previousMonth() = month.update { it.minusMonths(1) }
    fun nextMonth() = month.update { it.plusMonths(1) }

    fun save(draft: ExpenseDraft, onDone: () -> Unit) {
        val user = container.session.currentUser ?: return
        viewModelScope.launch { if (container.expenses.save(draft, user).report("Expense saved")) onDone() }
    }

    fun delete(id: String) {
        viewModelScope.launch { container.expenses.delete(id).report("Expense deleted") }
    }
}

private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

@Composable
fun ExpensesScreen() {
    val vm = appViewModel { ExpensesViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val symbol = settings.currencySymbol
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ExpenseEntity?>(null) }
    ShowMessages(vm)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SectionCard(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = vm::previousMonth) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.month.format(monthTitle), style = MaterialTheme.typography.titleMedium)
                        Text(Money.format(state.totalCents, symbol), style = MaterialTheme.typography.headlineSmall)
                        Text("${state.expenses.size} expense(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = vm::nextMonth, enabled = state.month < YearMonth.now()) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month")
                    }
                }
            }
            if (state.expenses.isEmpty()) {
                EmptyState(Icons.Filled.Payments, "No expenses this month", "Log rent, ingredients, salaries and other costs to see your real net profit.")
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.expenses, key = { it.id }) { expense ->
                        Card(
                            onClick = { editing = expense },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(expense.category, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        listOf(Dates.formatEpochDay(expense.spentOn), expense.description).filter { it.isNotBlank() }.joinToString(" · "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(Money.format(expense.amountCents, symbol), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Log expense") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }

    if (creating || editing != null) {
        ExpenseDialog(
            expense = editing,
            categories = state.categories,
            symbol = symbol,
            onSave = { draft -> vm.save(draft) { creating = false; editing = null } },
            onDelete = editing?.let { e -> { deleting = e; editing = null } },
            onDismiss = { creating = false; editing = null },
        )
    }
    deleting?.let { expense ->
        ConfirmDialog(
            title = "Delete expense?",
            message = "${expense.category}: ${Money.format(expense.amountCents, symbol)} on ${Dates.formatEpochDay(expense.spentOn)}",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { vm.delete(expense.id) },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun ExpenseDialog(
    expense: ExpenseEntity?,
    categories: List<String>,
    symbol: String,
    onSave: (ExpenseDraft) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(expense?.let { Money.plain(it.amountCents) } ?: "") }
    var date by remember { mutableStateOf<LocalDate?>(expense?.let { LocalDate.ofEpochDay(it.spentOn) } ?: Dates.today()) }
    var category by remember { mutableStateOf(expense?.category ?: "") }
    var description by remember { mutableStateOf(expense?.description ?: "") }
    var notes by remember { mutableStateOf(expense?.notes ?: "") }
    val cents = Money.parse(amount)
    val valid = cents != null && cents > 0 && date != null && category.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (expense == null) "Log expense" else "Edit expense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(amount, { amount = it }, "Amount", symbol = symbol, isError = amount.isNotBlank() && cents == null)
                DateField("Date", date, { date = it })
                SuggestField(category, { category = it }, "Category", categories)
                OutlinedTextField(description, { description = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (expense != null) {
                    Text(
                        "Logged by ${expense.createdBy}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(enabled = valid, onClick = {
                    val day = date
                    if (cents != null && day != null) {
                        onSave(ExpenseDraft(expense?.id, cents, day, category, description, notes))
                    }
                }) { Text("Save") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
