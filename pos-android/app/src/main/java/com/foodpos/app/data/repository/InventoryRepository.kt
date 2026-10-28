package com.foodpos.app.data.repository

import androidx.room.withTransaction
import com.foodpos.app.alerts.InventoryAlert
import com.foodpos.app.alerts.InventoryAlerts
import com.foodpos.app.alerts.ProductStock
import com.foodpos.app.alerts.StockCalculator
import com.foodpos.app.core.Dates
import com.foodpos.app.core.newId
import com.foodpos.app.data.local.BatchEntity
import com.foodpos.app.data.local.MovementRow
import com.foodpos.app.data.local.MovementType
import com.foodpos.app.data.local.PosDatabase
import com.foodpos.app.data.local.ProductEntity
import com.foodpos.app.data.local.StockMovementEntity
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.data.sync.SyncScheduler
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.random.Random

data class ProductDraft(
    val id: String?,
    val name: String,
    val category: String,
    val sku: String,
    val unit: String,
    val priceCents: Long,
    val lowStockThreshold: Int,
    val spoilSoonDays: Int,
)

data class DeliveryDraft(
    val productId: String,
    val batchNumber: String,
    val quantity: Int,
    val manufacturedOn: LocalDate?,
    val expiresOn: LocalDate?,
    val unitCostCents: Long?,
    val note: String,
)

/**
 * Rolling inventory. Stock lives in batches (batch number, manufacturing and expiry dates), and every
 * change to a batch is an immutable row in the stock movement ledger: deliveries, sales, manual
 * adjustments, spoilage write-offs and voided sales. A batch's balance is the sum of its ledger rows,
 * which also makes multi-device sync conflict-free: devices only ever add rows.
 */
class InventoryRepository(
    private val db: PosDatabase,
    private val settings: SettingsRepository,
    private val sync: SyncScheduler,
) {
    private val products = db.productDao()
    private val batches = db.batchDao()
    private val movements = db.movementDao()

    /** Emits today's date and again whenever the day changes, so freshness updates at midnight. */
    private val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            emit(Dates.today())
            delay(60_000)
        }
    }.distinctUntilChanged()

    fun observeStock(): Flow<List<ProductStock>> =
        combine(products.observeActive(), batches.observeAllWithStock(), todayFlow) { p, b, today ->
            StockCalculator.build(p, b, today)
        }

    fun observeAlerts(): Flow<List<InventoryAlert>> = observeStock().map(InventoryAlerts::compute)

    suspend fun currentAlerts(): List<InventoryAlert> =
        InventoryAlerts.compute(StockCalculator.build(products.active(), batches.allWithStock(), Dates.today()))

    fun observeLedger(limit: Int = 500): Flow<List<MovementRow>> = movements.observeLedger(limit)

    suspend fun saveProduct(draft: ProductDraft): Result<Unit> = runCatching {
        require(draft.name.isNotBlank()) { "Enter a product name" }
        require(draft.priceCents >= 0) { "Enter a valid price" }
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { products.findById(it) }
        products.upsert(
            ProductEntity(
                id = existing?.id ?: newId(),
                name = draft.name.trim(),
                category = draft.category.trim().ifEmpty { "General" },
                sku = draft.sku.trim(),
                unit = draft.unit.trim().ifEmpty { "pcs" },
                priceCents = draft.priceCents,
                lowStockThreshold = draft.lowStockThreshold.coerceAtLeast(0),
                spoilSoonDays = draft.spoilSoonDays.coerceAtLeast(0),
                isActive = true,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
        sync.requestSync()
    }

    suspend fun archiveProduct(productId: String): Result<Unit> = runCatching {
        val product = products.findById(productId) ?: error("Product not found")
        products.upsert(product.copy(isActive = false, updatedAt = System.currentTimeMillis(), dirty = true))
        sync.requestSync()
    }

    fun suggestBatchNumber(): String {
        val date = Dates.today().format(DateTimeFormatter.ofPattern("yyMMdd", Locale.US))
        val suffix = (1..3).map { BATCH_CHARS[Random.nextInt(BATCH_CHARS.length)] }.joinToString("")
        return "B$date-$suffix"
    }

    /** Records a new delivery: one new batch plus a DELIVERY ledger entry. */
    suspend fun receiveDelivery(draft: DeliveryDraft, user: UserEntity): Result<Unit> = runCatching {
        require(draft.quantity > 0) { "Quantity must be more than zero" }
        if (draft.manufacturedOn != null && draft.expiresOn != null) {
            require(!draft.expiresOn.isBefore(draft.manufacturedOn)) { "Expiry is before the manufacturing date" }
        }
        val now = System.currentTimeMillis()
        db.withTransaction {
            products.findById(draft.productId) ?: error("Product not found")
            val batch = BatchEntity(
                id = newId(),
                productId = draft.productId,
                batchNumber = draft.batchNumber.trim().ifEmpty { suggestBatchNumber() },
                manufacturedOn = draft.manufacturedOn?.toEpochDay(),
                expiresOn = draft.expiresOn?.toEpochDay(),
                receivedAt = now,
                receivedQuantity = draft.quantity,
                unitCostCents = draft.unitCostCents,
                note = draft.note.trim(),
                updatedAt = now,
            )
            batches.upsert(batch)
            movements.insertAll(
                listOf(movement(batch.productId, batch.id, MovementType.DELIVERY, draft.quantity, draft.note, user, now)),
            )
        }
        sync.requestSync()
    }

    /** Manual correction on one batch (count difference, damage, giveaway...). */
    suspend fun adjustStock(batchId: String, delta: Int, reason: String, user: UserEntity): Result<Unit> = runCatching {
        require(delta != 0) { "Enter a quantity to add or remove" }
        require(reason.isNotBlank()) { "Enter a reason for the adjustment" }
        db.withTransaction {
            val row = batches.findWithStock(batchId) ?: error("Batch not found")
            require(row.remaining + delta >= 0) { "Only ${row.remaining} left in this batch" }
            movements.insertAll(
                listOf(movement(row.batch.productId, batchId, MovementType.ADJUSTMENT, delta, reason, user)),
            )
        }
        sync.requestSync()
    }

    /** Removes everything left in a spoiled batch from stock. */
    suspend fun writeOffBatch(batchId: String, user: UserEntity, note: String = "Spoiled"): Result<Unit> = runCatching {
        db.withTransaction {
            val row = batches.findWithStock(batchId) ?: error("Batch not found")
            require(row.remaining > 0) { "This batch is already empty" }
            movements.insertAll(
                listOf(movement(row.batch.productId, batchId, MovementType.SPOILAGE, -row.remaining, note, user)),
            )
        }
        sync.requestSync()
    }

    private fun movement(
        productId: String,
        batchId: String,
        type: MovementType,
        quantity: Int,
        note: String,
        user: UserEntity,
        now: Long = System.currentTimeMillis(),
    ) = StockMovementEntity(
        id = newId(),
        productId = productId,
        batchId = batchId,
        type = type,
        quantity = quantity,
        note = note.trim(),
        orderId = null,
        userId = user.id,
        userName = user.fullName,
        createdAt = now,
        updatedAt = now,
    )

    private companion object {
        const val BATCH_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    }
}
