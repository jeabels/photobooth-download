package com.foodpos.app.data.repository

import com.foodpos.app.core.newId
import com.foodpos.app.data.local.ExpenseEntity
import com.foodpos.app.data.local.PosDatabase
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class ExpenseDraft(
    val id: String?,
    val amountCents: Long,
    val date: LocalDate,
    val category: String,
    val description: String,
    val notes: String,
)

class ExpenseRepository(private val db: PosDatabase, private val sync: SyncScheduler) {
    private val expenses = db.expenseDao()

    fun observeAll(): Flow<List<ExpenseEntity>> = expenses.observeAll()

    suspend fun save(draft: ExpenseDraft, user: UserEntity): Result<Unit> = runCatching {
        require(draft.amountCents > 0) { "Enter an amount" }
        require(draft.category.isNotBlank()) { "Choose a category" }
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { expenses.findById(it) }
        expenses.upsert(
            ExpenseEntity(
                id = existing?.id ?: newId(),
                amountCents = draft.amountCents,
                spentOn = draft.date.toEpochDay(),
                category = draft.category.trim(),
                description = draft.description.trim(),
                notes = draft.notes.trim(),
                createdBy = existing?.createdBy ?: user.fullName,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        sync.requestSync()
    }

    suspend fun delete(expenseId: String): Result<Unit> = runCatching {
        val expense = expenses.findById(expenseId) ?: error("Expense not found")
        expenses.upsert(expense.copy(deleted = true, updatedAt = System.currentTimeMillis(), dirty = true))
        sync.requestSync()
    }

    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Ingredients", "Packaging", "Rent", "Utilities", "Salaries",
            "Transport", "Equipment", "Maintenance", "Marketing", "Other",
        )
    }
}
