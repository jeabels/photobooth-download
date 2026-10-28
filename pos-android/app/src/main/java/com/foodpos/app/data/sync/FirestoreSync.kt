package com.foodpos.app.data.sync

import androidx.room.withTransaction
import com.foodpos.app.data.local.BatchEntity
import com.foodpos.app.data.local.ExpenseEntity
import com.foodpos.app.data.local.MovementType
import com.foodpos.app.data.local.OrderEntity
import com.foodpos.app.data.local.OrderItemEntity
import com.foodpos.app.data.local.OrderStatus
import com.foodpos.app.data.local.OrderType
import com.foodpos.app.data.local.PaymentMethod
import com.foodpos.app.data.local.PosDatabase
import com.foodpos.app.data.local.ProductEntity
import com.foodpos.app.data.local.Role
import com.foodpos.app.data.local.StockMovementEntity
import com.foodpos.app.data.local.UserEntity
import com.foodpos.app.data.local.UserStatus
import com.foodpos.app.data.repository.SettingsRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * One sync pass between Room and Firestore.
 *
 * Layout: businesses/{syncKey}/{users|products|batches|movements|orders|expenses}/{rowId}
 *
 * Push: every row with dirty = 1 is written (in batches of 400) with a server timestamp, then marked
 * clean, but only if it was not edited again while uploading.
 * Pull: documents whose server timestamp is newer than the last pull are merged into Room using
 * last-write-wins on updatedAt. Ledger rows and order lines are append-only, so stock never conflicts.
 */
internal class FirestoreSync(
    private val db: PosDatabase,
    private val settings: SettingsRepository,
) {
    private val firestore = FirebaseFirestore.getInstance()

    suspend fun run() {
        val key = settings.current.syncKey.trim()
        require(key.isNotEmpty()) { "Set a store sync key in Settings" }
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) auth.signInAnonymously().await()

        val store = firestore.collection("businesses").document(key)
        for (spec in specs()) {
            push(store, spec)
            pull(store, spec)
        }
    }

    private class Spec<T>(
        val name: String,
        val dirty: suspend () -> List<T>,
        val id: (T) -> String,
        val updatedAt: (T) -> Long,
        val toMap: suspend (T) -> Map<String, Any?>,
        val markSynced: suspend (String, Long) -> Unit,
        val merge: suspend (String, Map<String, Any?>) -> Unit,
    ) {
        suspend fun pending(): List<Pending> = dirty().map { Pending(id(it), updatedAt(it), toMap(it)) }
    }

    private class Pending(val id: String, val updatedAt: Long, val data: Map<String, Any?>)

    private fun specs(): List<Spec<*>> {
        val users = db.userDao()
        val products = db.productDao()
        val batches = db.batchDao()
        val movements = db.movementDao()
        val orders = db.orderDao()
        val expenses = db.expenseDao()
        return listOf(
            Spec("users", users::dirty, UserEntity::id, UserEntity::updatedAt, ::userToMap, users::markSynced) { id, m ->
                val remote = userFromMap(id, m)
                if (isNewer(remote.updatedAt, users.findById(id)?.updatedAt)) users.upsert(remote)
            },
            Spec("products", products::dirty, ProductEntity::id, ProductEntity::updatedAt, ::productToMap, products::markSynced) { id, m ->
                val remote = productFromMap(id, m)
                if (isNewer(remote.updatedAt, products.findById(id)?.updatedAt)) products.upsert(remote)
            },
            Spec("batches", batches::dirty, BatchEntity::id, BatchEntity::updatedAt, ::batchToMap, batches::markSynced) { id, m ->
                val remote = batchFromMap(id, m)
                if (isNewer(remote.updatedAt, batches.findById(id)?.updatedAt)) batches.upsert(remote)
            },
            Spec("movements", movements::dirty, StockMovementEntity::id, StockMovementEntity::updatedAt, ::movementToMap, movements::markSynced) { id, m ->
                val remote = movementFromMap(id, m)
                if (isNewer(remote.updatedAt, movements.findById(id)?.updatedAt)) movements.upsert(remote)
            },
            Spec(
                "orders", orders::dirty, OrderEntity::id, OrderEntity::updatedAt,
                { order -> orderToMap(order, orders.itemsFor(order.id)) }, orders::markSynced,
            ) { id, m ->
                val remote = orderFromMap(id, m)
                if (isNewer(remote.updatedAt, orders.findById(id)?.updatedAt)) {
                    orders.upsertOrder(remote)
                    orders.deleteItems(id)
                    orders.insertItems(itemsFromMap(id, m))
                }
            },
            Spec("expenses", expenses::dirty, ExpenseEntity::id, ExpenseEntity::updatedAt, ::expenseToMap, expenses::markSynced) { id, m ->
                val remote = expenseFromMap(id, m)
                if (isNewer(remote.updatedAt, expenses.findById(id)?.updatedAt)) expenses.upsert(remote)
            },
        )
    }

    private suspend fun push(store: DocumentReference, spec: Spec<*>) {
        val collection = store.collection(spec.name)
        for (chunk in spec.pending().chunked(BATCH_SIZE)) {
            val batch = firestore.batch()
            for (row in chunk) {
                batch.set(collection.document(row.id), row.data + (SERVER_TIME to FieldValue.serverTimestamp()))
            }
            batch.commit().await()
            for (row in chunk) spec.markSynced(row.id, row.updatedAt)
        }
    }

    private suspend fun pull(store: DocumentReference, spec: Spec<*>) {
        val collection: CollectionReference = store.collection(spec.name)
        val cursor = settings.pullCursor(spec.name)
        // Re-read a short overlap so documents committed at almost the same moment are not missed.
        val since = Timestamp(Date((cursor - OVERLAP_MS).coerceAtLeast(0)))
        var query: Query = collection.whereGreaterThan(SERVER_TIME, since).orderBy(SERVER_TIME).limit(PAGE_SIZE.toLong())
        var newest = cursor
        while (true) {
            val page = query.get(Source.SERVER).await()
            if (page.isEmpty) break
            db.withTransaction {
                for (doc in page.documents) {
                    val data = doc.data ?: continue
                    spec.merge(doc.id, data)
                }
            }
            for (doc in page.documents) {
                val time = doc.getTimestamp(SERVER_TIME)?.toDate()?.time ?: continue
                if (time > newest) newest = time
            }
            if (page.size() < PAGE_SIZE) break
            query = query.startAfter(page.documents.last())
        }
        settings.setPullCursor(spec.name, newest)
    }

    /** Last write wins; ties keep the local row. */
    private fun isNewer(remoteUpdatedAt: Long, localUpdatedAt: Long?): Boolean =
        localUpdatedAt == null || remoteUpdatedAt > localUpdatedAt

    /* ---- Mapping between Room rows and Firestore documents ---- */

    private fun userToMap(u: UserEntity) = mapOf(
        "username" to u.username, "fullName" to u.fullName, "passwordHash" to u.passwordHash, "salt" to u.salt,
        "role" to u.role.name, "status" to u.status.name, "createdAt" to u.createdAt, "updatedAt" to u.updatedAt,
    )

    private fun userFromMap(id: String, m: Map<String, Any?>) = UserEntity(
        id = id, username = m.str("username"), fullName = m.str("fullName"), passwordHash = m.str("passwordHash"),
        salt = m.str("salt"), role = m.enum("role", Role.STAFF), status = m.enum("status", UserStatus.PENDING),
        createdAt = m.long("createdAt"), updatedAt = m.long("updatedAt"), dirty = false,
    )

    private fun productToMap(p: ProductEntity) = mapOf(
        "name" to p.name, "category" to p.category, "sku" to p.sku, "unit" to p.unit, "priceCents" to p.priceCents,
        "lowStockThreshold" to p.lowStockThreshold, "spoilSoonDays" to p.spoilSoonDays, "isActive" to p.isActive,
        "createdAt" to p.createdAt, "updatedAt" to p.updatedAt,
    )

    private fun productFromMap(id: String, m: Map<String, Any?>) = ProductEntity(
        id = id, name = m.str("name"), category = m.str("category"), sku = m.str("sku"), unit = m.str("unit"),
        priceCents = m.long("priceCents"), lowStockThreshold = m.int("lowStockThreshold"),
        spoilSoonDays = m.int("spoilSoonDays"), isActive = m.bool("isActive", true),
        createdAt = m.long("createdAt"), updatedAt = m.long("updatedAt"), dirty = false,
    )

    private fun batchToMap(b: BatchEntity) = mapOf(
        "productId" to b.productId, "batchNumber" to b.batchNumber, "manufacturedOn" to b.manufacturedOn,
        "expiresOn" to b.expiresOn, "receivedAt" to b.receivedAt, "receivedQuantity" to b.receivedQuantity,
        "unitCostCents" to b.unitCostCents, "note" to b.note, "updatedAt" to b.updatedAt,
    )

    private fun batchFromMap(id: String, m: Map<String, Any?>) = BatchEntity(
        id = id, productId = m.str("productId"), batchNumber = m.str("batchNumber"),
        manufacturedOn = m.longOrNull("manufacturedOn"), expiresOn = m.longOrNull("expiresOn"),
        receivedAt = m.long("receivedAt"), receivedQuantity = m.int("receivedQuantity"),
        unitCostCents = m.longOrNull("unitCostCents"), note = m.str("note"), updatedAt = m.long("updatedAt"), dirty = false,
    )

    private fun movementToMap(s: StockMovementEntity) = mapOf(
        "productId" to s.productId, "batchId" to s.batchId, "type" to s.type.name, "quantity" to s.quantity,
        "note" to s.note, "orderId" to s.orderId, "userId" to s.userId, "userName" to s.userName,
        "createdAt" to s.createdAt, "updatedAt" to s.updatedAt,
    )

    private fun movementFromMap(id: String, m: Map<String, Any?>) = StockMovementEntity(
        id = id, productId = m.str("productId"), batchId = m.strOrNull("batchId"),
        type = m.enum("type", MovementType.ADJUSTMENT), quantity = m.int("quantity"), note = m.str("note"),
        orderId = m.strOrNull("orderId"), userId = m.str("userId"), userName = m.str("userName"),
        createdAt = m.long("createdAt"), updatedAt = m.long("updatedAt"), dirty = false,
    )

    private fun orderToMap(o: OrderEntity, items: List<OrderItemEntity>) = mapOf(
        "queueNumber" to o.queueNumber, "queueDay" to o.queueDay, "sequence" to o.sequence,
        "deviceCode" to o.deviceCode, "createdAt" to o.createdAt, "orderType" to o.orderType.name,
        "customerName" to o.customerName, "customerPhone" to o.customerPhone,
        "customerAddress" to o.customerAddress, "notes" to o.notes, "subtotalCents" to o.subtotalCents,
        "deliveryFeeCents" to o.deliveryFeeCents, "totalCents" to o.totalCents,
        "paymentMethod" to o.paymentMethod.name, "amountTenderedCents" to o.amountTenderedCents,
        "changeCents" to o.changeCents, "paymentReference" to o.paymentReference, "status" to o.status.name,
        "cashierId" to o.cashierId, "cashierName" to o.cashierName, "updatedAt" to o.updatedAt,
        "items" to items.map {
            mapOf(
                "id" to it.id, "productId" to it.productId, "productName" to it.productName,
                "unitPriceCents" to it.unitPriceCents, "quantity" to it.quantity, "lineTotalCents" to it.lineTotalCents,
            )
        },
    )

    private fun orderFromMap(id: String, m: Map<String, Any?>) = OrderEntity(
        id = id, queueNumber = m.str("queueNumber"), queueDay = m.str("queueDay"), sequence = m.int("sequence"),
        deviceCode = m.str("deviceCode"), createdAt = m.long("createdAt"),
        orderType = m.enum("orderType", OrderType.WALK_IN), customerName = m.str("customerName"),
        customerPhone = m.str("customerPhone"), customerAddress = m.str("customerAddress"), notes = m.str("notes"),
        subtotalCents = m.long("subtotalCents"), deliveryFeeCents = m.long("deliveryFeeCents"),
        totalCents = m.long("totalCents"), paymentMethod = m.enum("paymentMethod", PaymentMethod.CASH),
        amountTenderedCents = m.longOrNull("amountTenderedCents"), changeCents = m.long("changeCents"),
        paymentReference = m.str("paymentReference"), status = m.enum("status", OrderStatus.COMPLETED),
        cashierId = m.str("cashierId"), cashierName = m.str("cashierName"), updatedAt = m.long("updatedAt"),
        dirty = false,
    )

    private fun itemsFromMap(orderId: String, m: Map<String, Any?>): List<OrderItemEntity> =
        (m["items"] as? List<*>).orEmpty().filterIsInstance<Map<String, Any?>>().map {
            OrderItemEntity(
                id = it.str("id"), orderId = orderId, productId = it.str("productId"),
                productName = it.str("productName"), unitPriceCents = it.long("unitPriceCents"),
                quantity = it.int("quantity"), lineTotalCents = it.long("lineTotalCents"),
            )
        }

    private fun expenseToMap(e: ExpenseEntity) = mapOf(
        "amountCents" to e.amountCents, "spentOn" to e.spentOn, "category" to e.category,
        "description" to e.description, "notes" to e.notes, "createdBy" to e.createdBy,
        "createdAt" to e.createdAt, "deleted" to e.deleted, "updatedAt" to e.updatedAt,
    )

    private fun expenseFromMap(id: String, m: Map<String, Any?>) = ExpenseEntity(
        id = id, amountCents = m.long("amountCents"), spentOn = m.long("spentOn"), category = m.str("category"),
        description = m.str("description"), notes = m.str("notes"), createdBy = m.str("createdBy"),
        createdAt = m.long("createdAt"), deleted = m.bool("deleted", false), updatedAt = m.long("updatedAt"),
        dirty = false,
    )

    private companion object {
        const val SERVER_TIME = "serverUpdatedAt"
        const val BATCH_SIZE = 400
        const val PAGE_SIZE = 300
        const val OVERLAP_MS = 60_000L
    }
}

private fun Map<String, Any?>.str(key: String): String = this[key] as? String ?: ""
private fun Map<String, Any?>.strOrNull(key: String): String? = this[key] as? String
private fun Map<String, Any?>.long(key: String): Long = (this[key] as? Number)?.toLong() ?: 0L
private fun Map<String, Any?>.longOrNull(key: String): Long? = (this[key] as? Number)?.toLong()
private fun Map<String, Any?>.int(key: String): Int = (this[key] as? Number)?.toInt() ?: 0
private fun Map<String, Any?>.bool(key: String, default: Boolean): Boolean = this[key] as? Boolean ?: default
private inline fun <reified E : Enum<E>> Map<String, Any?>.enum(key: String, default: E): E =
    runCatching { enumValueOf<E>(str(key)) }.getOrDefault(default)
