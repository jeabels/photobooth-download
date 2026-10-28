package com.foodpos.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT COUNT(*) FROM users")
    suspend fun count(): Int

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun findByUsername(username: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun findById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users ORDER BY fullName COLLATE NOCASE")
    fun observeAll(): Flow<List<UserEntity>>

    @Query("SELECT COUNT(*) FROM users WHERE role = 'OWNER' AND status = 'APPROVED'")
    suspend fun countActiveOwners(): Int

    @Upsert
    suspend fun upsert(user: UserEntity)

    @Query("SELECT * FROM users WHERE dirty = 1")
    suspend fun dirty(): List<UserEntity>

    @Query("UPDATE users SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name COLLATE NOCASE")
    suspend fun active(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun findById(id: String): ProductEntity?

    @Upsert
    suspend fun upsert(product: ProductEntity)

    @Query("SELECT * FROM products WHERE dirty = 1")
    suspend fun dirty(): List<ProductEntity>

    @Query("UPDATE products SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
}

private const val BATCH_WITH_STOCK =
    "SELECT b.*, COALESCE((SELECT SUM(m.quantity) FROM stock_movements m WHERE m.batchId = b.id), 0) " +
        "AS remaining FROM batches b"

/** First-expiring first: batches with no expiry date go last. */
private const val FEFO_ORDER = " ORDER BY b.expiresOn IS NULL, b.expiresOn, b.receivedAt"

@Dao
interface BatchDao {
    @Query(BATCH_WITH_STOCK + FEFO_ORDER)
    fun observeAllWithStock(): Flow<List<BatchWithStock>>

    @Query(BATCH_WITH_STOCK + FEFO_ORDER)
    suspend fun allWithStock(): List<BatchWithStock>

    @Query("$BATCH_WITH_STOCK WHERE b.productId = :productId$FEFO_ORDER")
    suspend fun forProduct(productId: String): List<BatchWithStock>

    @Query("$BATCH_WITH_STOCK WHERE b.id = :id")
    suspend fun findWithStock(id: String): BatchWithStock?

    @Query("SELECT * FROM batches WHERE id = :id")
    suspend fun findById(id: String): BatchEntity?

    @Upsert
    suspend fun upsert(batch: BatchEntity)

    @Query("SELECT * FROM batches WHERE dirty = 1")
    suspend fun dirty(): List<BatchEntity>

    @Query("UPDATE batches SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
}

@Dao
interface MovementDao {
    @Insert
    suspend fun insertAll(movements: List<StockMovementEntity>)

    @Upsert
    suspend fun upsert(movement: StockMovementEntity)

    @Query("SELECT * FROM stock_movements WHERE id = :id")
    suspend fun findById(id: String): StockMovementEntity?

    @Query(
        "SELECT m.*, p.name AS productName, b.batchNumber AS batchNumber FROM stock_movements m " +
            "LEFT JOIN products p ON p.id = m.productId LEFT JOIN batches b ON b.id = m.batchId " +
            "ORDER BY m.createdAt DESC LIMIT :limit",
    )
    fun observeLedger(limit: Int): Flow<List<MovementRow>>

    @Query("SELECT * FROM stock_movements WHERE orderId = :orderId AND type = 'SALE'")
    suspend fun saleMovementsForOrder(orderId: String): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE dirty = 1")
    suspend fun dirty(): List<StockMovementEntity>

    @Query("UPDATE stock_movements SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
}

@Dao
interface OrderDao {
    @Insert
    suspend fun insertOrder(order: OrderEntity)

    @Upsert
    suspend fun upsertOrder(order: OrderEntity)

    @Insert
    suspend fun insertItems(items: List<OrderItemEntity>)

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteItems(orderId: String)

    @Query("SELECT MAX(sequence) FROM orders WHERE queueDay = :day AND deviceCode = :deviceCode")
    suspend fun maxSequence(day: String, deviceCode: String): Int?

    @Transaction
    @Query("SELECT * FROM orders WHERE createdAt >= :from ORDER BY createdAt DESC")
    fun observeSince(from: Long): Flow<List<OrderWithItems>>

    @Transaction
    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun findWithItems(id: String): OrderWithItems?

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun findById(id: String): OrderEntity?

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun itemsFor(orderId: String): List<OrderItemEntity>

    @Query("SELECT createdAt, totalCents FROM orders WHERE status = 'COMPLETED'")
    fun observeSalePoints(): Flow<List<SalePoint>>

    @Query("SELECT * FROM orders WHERE dirty = 1")
    suspend fun dirty(): List<OrderEntity>

    @Query("UPDATE orders SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE deleted = 0 ORDER BY spentOn DESC, createdAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: String): ExpenseEntity?

    @Upsert
    suspend fun upsert(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses WHERE dirty = 1")
    suspend fun dirty(): List<ExpenseEntity>

    @Query("UPDATE expenses SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markSynced(id: String, updatedAt: Long)
}
