package com.foodpos.app.data.repository

import androidx.room.withTransaction
import com.foodpos.app.alerts.StockCalculator
import com.foodpos.app.core.Dates
import com.foodpos.app.core.newId
import com.foodpos.app.data.local.MovementType
import com.foodpos.app.data.local.OrderEntity
import com.foodpos.app.data.local.OrderItemEntity
import com.foodpos.app.data.local.OrderStatus
import com.foodpos.app.data.local.OrderType
import com.foodpos.app.data.local.OrderWithItems
import com.foodpos.app.data.local.PaymentMethod
import com.foodpos.app.data.local.PosDatabase
import com.foodpos.app.data.local.SalePoint
import com.foodpos.app.data.local.StockMovementEntity
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.data.sync.SyncScheduler
import kotlinx.coroutines.flow.Flow
import java.util.Locale

data class CheckoutRequest(
    /** productId to quantity, in the order the items were added. */
    val lines: Map<String, Int>,
    val orderType: OrderType,
    val deliveryFeeCents: Long,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val notes: String,
    val paymentMethod: PaymentMethod,
    val amountTenderedCents: Long?,
    val paymentReference: String,
)

class CheckoutException(message: String) : Exception(message)

class OrderRepository(
    private val db: PosDatabase,
    private val settings: SettingsRepository,
    private val sync: SyncScheduler,
) {
    private val orders = db.orderDao()
    private val products = db.productDao()
    private val batches = db.batchDao()
    private val movements = db.movementDao()

    fun observeSince(fromMillis: Long): Flow<List<OrderWithItems>> = orders.observeSince(fromMillis)

    fun observeSalePoints(): Flow<List<SalePoint>> = orders.observeSalePoints()

    suspend fun find(orderId: String): OrderWithItems? = orders.findWithItems(orderId)

    /**
     * Places an order in one database transaction: checks stock, takes the next queue number, saves
     * the order and its lines, and deducts stock from the batches that expire first (FEFO).
     * Either everything is saved or nothing is.
     */
    suspend fun checkout(request: CheckoutRequest, cashier: UserEntity): OrderWithItems {
        if (request.lines.isEmpty() || request.lines.values.any { it <= 0 }) throw CheckoutException("The cart is empty")
        if (request.deliveryFeeCents < 0) throw CheckoutException("Enter a valid delivery fee")
        if (request.orderType == OrderType.DELIVERY && request.customerAddress.isBlank()) {
            throw CheckoutException("Enter the delivery address")
        }
        val settings = settings.current
        val result = db.withTransaction {
            val now = System.currentTimeMillis()
            val today = Dates.today()
            val orderId = newId()
            val items = mutableListOf<OrderItemEntity>()
            val stockOut = mutableListOf<StockMovementEntity>()

            for ((productId, quantity) in request.lines) {
                val product = products.findById(productId)?.takeIf { it.isActive }
                    ?: throw CheckoutException("A product in the cart is no longer available")
                val sellable = batches.forProduct(productId)
                    .filter { it.remaining > 0 && !StockCalculator.isSpoiled(it.batch.expiresOn, today) }
                val available = sellable.sumOf { it.remaining }
                if (available < quantity) {
                    throw CheckoutException("Only $available ${product.unit} of ${product.name} left in stock")
                }
                var needed = quantity
                for (row in sellable) {
                    if (needed == 0) break
                    val take = minOf(needed, row.remaining)
                    stockOut += StockMovementEntity(
                        id = newId(),
                        productId = productId,
                        batchId = row.batch.id,
                        type = MovementType.SALE,
                        quantity = -take,
                        note = "",
                        orderId = orderId,
                        userId = cashier.id,
                        userName = cashier.fullName,
                        createdAt = now,
                        updatedAt = now,
                    )
                    needed -= take
                }
                items += OrderItemEntity(
                    id = newId(),
                    orderId = orderId,
                    productId = productId,
                    productName = product.name,
                    unitPriceCents = product.priceCents,
                    quantity = quantity,
                    lineTotalCents = product.priceCents * quantity,
                )
            }

            val subtotal = items.sumOf { it.lineTotalCents }
            val deliveryFee = if (request.orderType == OrderType.DELIVERY) request.deliveryFeeCents else 0L
            val total = subtotal + deliveryFee
            val tendered = if (request.paymentMethod == PaymentMethod.CASH) request.amountTenderedCents ?: total else null
            if (tendered != null && tendered < total) throw CheckoutException("Cash received is less than the total")

            val deviceCode = settings.deviceCode.ifBlank { "A" }
            val day = today.toString()
            val sequence = (orders.maxSequence(day, deviceCode) ?: 0) + 1
            val order = OrderEntity(
                id = orderId,
                queueNumber = String.format(Locale.US, "%s-%03d", deviceCode, sequence),
                queueDay = day,
                sequence = sequence,
                deviceCode = deviceCode,
                createdAt = now,
                orderType = request.orderType,
                customerName = request.customerName.trim(),
                customerPhone = request.customerPhone.trim(),
                customerAddress = if (request.orderType == OrderType.DELIVERY) request.customerAddress.trim() else "",
                notes = request.notes.trim(),
                subtotalCents = subtotal,
                deliveryFeeCents = deliveryFee,
                totalCents = total,
                paymentMethod = request.paymentMethod,
                amountTenderedCents = tendered,
                changeCents = if (tendered != null) tendered - total else 0,
                paymentReference = if (request.paymentMethod == PaymentMethod.CASH) "" else request.paymentReference.trim(),
                status = OrderStatus.COMPLETED,
                cashierId = cashier.id,
                cashierName = cashier.fullName,
                updatedAt = now,
            )
            orders.insertOrder(order)
            orders.insertItems(items)
            movements.insertAll(stockOut)
            OrderWithItems(order, items)
        }
        sync.requestSync()
        return result
    }

    /** Owner-only: cancels a completed order and puts its items back into the batches they came from. */
    suspend fun voidOrder(orderId: String, user: UserEntity, reason: String): Result<Unit> = runCatching {
        db.withTransaction {
            val order = orders.findById(orderId) ?: error("Order not found")
            check(order.status == OrderStatus.COMPLETED) { "This order is already voided" }
            val now = System.currentTimeMillis()
            orders.upsertOrder(order.copy(status = OrderStatus.VOIDED, updatedAt = now, dirty = true))
            val returns = movements.saleMovementsForOrder(orderId).map {
                it.copy(
                    id = newId(),
                    type = MovementType.VOID_RETURN,
                    quantity = -it.quantity,
                    note = reason.ifBlank { "Order ${order.queueNumber} voided" },
                    userId = user.id,
                    userName = user.fullName,
                    createdAt = now,
                    updatedAt = now,
                    dirty = true,
                )
            }
            movements.insertAll(returns)
        }
        sync.requestSync()
    }
}
