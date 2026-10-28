package com.foodpos.app

import com.foodpos.app.alerts.AlertType
import com.foodpos.app.alerts.BatchStatus
import com.foodpos.app.alerts.InventoryAlerts
import com.foodpos.app.alerts.StockCalculator
import com.foodpos.app.alerts.StockLevel
import com.foodpos.app.data.local.BatchEntity
import com.foodpos.app.data.local.BatchWithStock
import com.foodpos.app.data.local.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InventoryAlertsTest {
    private val today = LocalDate.of(2026, 10, 28)

    private fun product(id: String, low: Int = 5, spoilDays: Int = 3) = ProductEntity(
        id = id, name = "Product $id", category = "Meals", sku = "", unit = "pcs", priceCents = 10_000,
        lowStockThreshold = low, spoilSoonDays = spoilDays, createdAt = 0, updatedAt = 0,
    )

    private fun batch(productId: String, remaining: Int, expires: LocalDate?) = BatchWithStock(
        BatchEntity(
            id = "$productId-${expires ?: "none"}-$remaining", productId = productId, batchNumber = "B1",
            manufacturedOn = null, expiresOn = expires?.toEpochDay(), receivedAt = 0, receivedQuantity = remaining,
            unitCostCents = null, note = "", updatedAt = 0,
        ),
        remaining,
    )

    @Test
    fun `batch freshness follows expiry and threshold days`() {
        val p = product("a", spoilDays = 3)
        val stock = StockCalculator.build(
            listOf(p),
            listOf(
                batch("a", 4, today.minusDays(1)),
                batch("a", 4, today),
                batch("a", 4, today.plusDays(3)),
                batch("a", 4, today.plusDays(4)),
                batch("a", 0, today.minusDays(9)),
                batch("a", 4, null),
            ),
            today,
        ).single()
        assertEquals(
            listOf(BatchStatus.SPOILED, BatchStatus.SPOILING_SOON, BatchStatus.SPOILING_SOON, BatchStatus.FRESH, BatchStatus.EMPTY, BatchStatus.FRESH),
            stock.batches.map { it.status },
        )
        assertEquals(20, stock.onHand)
        assertEquals(16, stock.sellable) // the spoiled batch can't be sold
    }

    @Test
    fun `alerts for out of stock, running low, spoiled and spoiling soon`() {
        val stock = StockCalculator.build(
            listOf(product("out"), product("low", low = 5), product("ok", low = 2)),
            listOf(
                batch("out", 3, today.minusDays(2)), // all spoiled: out of stock + spoiled
                batch("low", 5, today.plusDays(1)), // running low + spoiling soon
                batch("ok", 10, today.plusDays(30)),
            ),
            today,
        )
        assertEquals(StockLevel.OUT_OF_STOCK, stock[0].level)
        assertEquals(StockLevel.RUNNING_LOW, stock[1].level)
        assertEquals(StockLevel.IN_STOCK, stock[2].level)

        val alerts = InventoryAlerts.compute(stock)
        assertEquals(
            listOf(AlertType.OUT_OF_STOCK, AlertType.SPOILED, AlertType.SPOILING_SOON, AlertType.RUNNING_LOW),
            alerts.map { it.type },
        )
        assertTrue(alerts.first { it.type == AlertType.SPOILING_SOON }.detail.contains("tomorrow"))
        assertEquals("1 Out of Stock · 1 Spoiled · 1 Spoiling Soon · 1 Running Low", InventoryAlerts.summary(alerts))
    }
}
