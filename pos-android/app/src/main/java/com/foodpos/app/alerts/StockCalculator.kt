package com.foodpos.app.alerts

import com.foodpos.app.data.local.BatchEntity
import com.foodpos.app.data.local.BatchWithStock
import com.foodpos.app.data.local.ProductEntity
import java.time.LocalDate

enum class BatchStatus { FRESH, SPOILING_SOON, SPOILED, EMPTY }

enum class StockLevel { IN_STOCK, RUNNING_LOW, OUT_OF_STOCK }

data class BatchStock(
    val batch: BatchEntity,
    val remaining: Int,
    val status: BatchStatus,
    /** Days until expiry (0 = expires today, negative = spoiled); null when there is no expiry date. */
    val daysLeft: Long?,
)

data class ProductStock(
    val product: ProductEntity,
    val batches: List<BatchStock>,
) {
    /** Everything on the shelf, including spoiled batches not yet written off. */
    val onHand: Int get() = batches.sumOf { it.remaining.coerceAtLeast(0) }

    /** What can be sold: stock in batches that have not spoiled. */
    val sellable: Int get() = batches.filter { it.status != BatchStatus.SPOILED }.sumOf { it.remaining.coerceAtLeast(0) }

    val level: StockLevel
        get() = when {
            sellable <= 0 -> StockLevel.OUT_OF_STOCK
            sellable <= product.lowStockThreshold -> StockLevel.RUNNING_LOW
            else -> StockLevel.IN_STOCK
        }
}

/** Joins products with their batch balances and works out freshness for a given day. */
object StockCalculator {
    fun build(
        products: List<ProductEntity>,
        batches: List<BatchWithStock>,
        today: LocalDate,
    ): List<ProductStock> {
        val byProduct = batches.groupBy { it.batch.productId }
        return products.map { product ->
            val productBatches = byProduct[product.id].orEmpty().map { row ->
                batchStatus(row, product.spoilSoonDays, today)
            }
            ProductStock(product, productBatches)
        }
    }

    fun batchStatus(row: BatchWithStock, spoilSoonDays: Int, today: LocalDate): BatchStock {
        val daysLeft = row.batch.expiresOn?.let { it - today.toEpochDay() }
        val status = when {
            row.remaining <= 0 -> BatchStatus.EMPTY
            daysLeft == null -> BatchStatus.FRESH
            daysLeft < 0 -> BatchStatus.SPOILED
            daysLeft <= spoilSoonDays -> BatchStatus.SPOILING_SOON
            else -> BatchStatus.FRESH
        }
        return BatchStock(row.batch, row.remaining, status, daysLeft)
    }

    fun isSpoiled(expiresOn: Long?, today: LocalDate): Boolean =
        expiresOn != null && expiresOn < today.toEpochDay()
}
