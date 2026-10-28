package com.foodpos.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** The local source of truth. The app reads and writes only here; Firestore is a background mirror. */
@Database(
    entities = [
        UserEntity::class,
        ProductEntity::class,
        BatchEntity::class,
        StockMovementEntity::class,
        OrderEntity::class,
        OrderItemEntity::class,
        ExpenseEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class PosDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun productDao(): ProductDao
    abstract fun batchDao(): BatchDao
    abstract fun movementDao(): MovementDao
    abstract fun orderDao(): OrderDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        fun build(context: Context): PosDatabase =
            Room.databaseBuilder(context, PosDatabase::class.java, "pos.db").build()
    }
}
