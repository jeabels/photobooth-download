package com.foodpos.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/*
 * Sync columns used by every table:
 *  - updatedAt: last local change (epoch millis), used for last-write-wins when merging cloud data.
 *  - dirty: true until the row has been uploaded to Firestore.
 *
 * Rows are never hard-deleted (products are archived, expenses soft-deleted) so deletions sync too.
 * There are no foreign keys: synced rows can arrive in any order.
 */

enum class Role { OWNER, STAFF }

enum class UserStatus { PENDING, APPROVED, REJECTED }

enum class PaymentMethod(val label: String) {
    CASH("Cash"),
    E_WALLET("E-Wallet"),
    BANK_TRANSFER("Bank Transfer"),
}

enum class OrderType(val label: String) {
    WALK_IN("Walk-in / Pick-up"),
    DELIVERY("Delivery"),
}

enum class OrderStatus { COMPLETED, VOIDED }

/** Every stock change is one immutable ledger row. Stock on hand is the sum of the ledger. */
enum class MovementType(val label: String) {
    DELIVERY("New delivery"),
    SALE("Sale"),
    ADJUSTMENT("Manual adjustment"),
    SPOILAGE("Spoilage write-off"),
    VOID_RETURN("Voided sale"),
}

@Entity(tableName = "users", indices = [Index("username")])
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val fullName: String,
    /** "sha256$<iterations>$<hex>", see [com.foodpos.app.security.PasswordHasher]. */
    val passwordHash: String,
    /** Base64 random salt, unique per user. */
    val salt: String,
    val role: Role,
    val status: UserStatus,
    val createdAt: Long,
    val updatedAt: Long,
    val dirty: Boolean = true,
)

@Entity(tableName = "products", indices = [Index("category")])
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val category: String,
    val sku: String,
    val unit: String,
    val priceCents: Long,
    /** "Running Low" when sellable stock is at or below this. */
    val lowStockThreshold: Int,
    /** "Spoiling Soon" when a batch expires within this many days. */
    val spoilSoonDays: Int,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long,
    val dirty: Boolean = true,
)

@Entity(tableName = "batches", indices = [Index("productId")])
data class BatchEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val batchNumber: String,
    /** Epoch day (LocalDate.toEpochDay) or null when unknown. */
    val manufacturedOn: Long?,
    /** Epoch day; the batch is spoiled from the day after this date. Null = does not expire. */
    val expiresOn: Long?,
    val receivedAt: Long,
    /** Quantity in the delivery, for reference. Current stock comes from the ledger. */
    val receivedQuantity: Int,
    val unitCostCents: Long?,
    val note: String,
    val updatedAt: Long,
    val dirty: Boolean = true,
)

@Entity(
    tableName = "stock_movements",
    indices = [Index("productId"), Index("batchId"), Index("orderId"), Index("createdAt")],
)
data class StockMovementEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val batchId: String?,
    val type: MovementType,
    /** Signed change: +12 for a delivery, -2 for a sale. */
    val quantity: Int,
    val note: String,
    val orderId: String?,
    val userId: String,
    val userName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val dirty: Boolean = true,
)

@Entity(tableName = "orders", indices = [Index("createdAt"), Index("queueDay")])
data class OrderEntity(
    @PrimaryKey val id: String,
    /** Printed queue number, e.g. "A-012". Restarts every day, per device. */
    val queueNumber: String,
    /** ISO date (yyyy-MM-dd) the queue sequence belongs to. */
    val queueDay: String,
    val sequence: Int,
    val deviceCode: String,
    val createdAt: Long,
    val orderType: OrderType,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val notes: String,
    val subtotalCents: Long,
    val deliveryFeeCents: Long,
    val totalCents: Long,
    val paymentMethod: PaymentMethod,
    /** Cash handed over; null for non-cash payments. */
    val amountTenderedCents: Long?,
    val changeCents: Long,
    /** E-wallet or bank transfer reference number. */
    val paymentReference: String,
    val status: OrderStatus,
    val cashierId: String,
    val cashierName: String,
    val updatedAt: Long,
    val dirty: Boolean = true,
)

/** Order lines are synced inside their order's document, so they carry no sync columns. */
@Entity(tableName = "order_items", indices = [Index("orderId")])
data class OrderItemEntity(
    @PrimaryKey val id: String,
    val orderId: String,
    val productId: String,
    val productName: String,
    val unitPriceCents: Long,
    val quantity: Int,
    val lineTotalCents: Long,
)

@Entity(tableName = "expenses", indices = [Index("spentOn")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val amountCents: Long,
    /** Epoch day of the expense. */
    val spentOn: Long,
    val category: String,
    val description: String,
    val notes: String,
    val createdBy: String,
    val createdAt: Long,
    val deleted: Boolean = false,
    val updatedAt: Long,
    val dirty: Boolean = true,
)

/* ---- Query results ---- */

data class BatchWithStock(
    @Embedded val batch: BatchEntity,
    val remaining: Int,
)

data class MovementRow(
    @Embedded val movement: StockMovementEntity,
    val productName: String?,
    val batchNumber: String?,
)

data class OrderWithItems(
    @Embedded val order: OrderEntity,
    @Relation(parentColumn = "id", entityColumn = "orderId")
    val items: List<OrderItemEntity>,
)

data class SalePoint(
    val createdAt: Long,
    val totalCents: Long,
)
