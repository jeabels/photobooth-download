package com.foodpos.app

import com.foodpos.app.data.local.OrderEntity
import com.foodpos.app.data.local.OrderItemEntity
import com.foodpos.app.data.local.OrderStatus
import com.foodpos.app.data.local.OrderType
import com.foodpos.app.data.local.OrderWithItems
import com.foodpos.app.data.local.PaymentMethod
import com.foodpos.app.data.repository.AppSettings
import com.foodpos.app.printer.ReceiptFormatter
import com.foodpos.app.printer.ReceiptLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptFormatterTest {
    private val order = OrderWithItems(
        OrderEntity(
            id = "o1", queueNumber = "A-007", queueDay = "2026-10-28", sequence = 7, deviceCode = "A", createdAt = 0,
            orderType = OrderType.DELIVERY, customerName = "Ana", customerPhone = "0917", customerAddress = "12 Mabini St",
            notes = "", subtotalCents = 27_000, deliveryFeeCents = 5_000, totalCents = 32_000,
            paymentMethod = PaymentMethod.CASH, amountTenderedCents = 50_000, changeCents = 18_000, paymentReference = "",
            status = OrderStatus.COMPLETED, cashierId = "u1", cashierName = "Ben", updatedAt = 0,
        ),
        listOf(
            OrderItemEntity("i1", "o1", "p1", "Chicken Adobo", 12_000, 2, 24_000),
            OrderItemEntity("i2", "o1", "p2", "Iced Tea", 3_000, 1, 3_000),
        ),
    )

    @Test
    fun `receipt has centred bold header, unit prices, bold total and change`() {
        val receipt = ReceiptFormatter.build(order, AppSettings(businessName = "Lutong Bahay"), logo = null, printedAt = "Oct 28, 2026 12:00 PM")
        val lines = receipt.lines

        val name = lines.first() as ReceiptLine.Text
        assertEquals("Lutong Bahay", name.text)
        assertTrue(name.bold && name.large)

        assertTrue(lines.contains(ReceiptLine.Columns("  2 x 120.00", "240.00")))
        assertTrue(lines.contains(ReceiptLine.Columns("Delivery fee", "50.00")))
        val total = lines.filterIsInstance<ReceiptLine.Columns>().first { it.left == "TOTAL" }
        assertEquals("₱320.00", total.right)
        assertTrue(total.bold)
        assertTrue(lines.contains(ReceiptLine.Columns("Change", "180.00", bold = true)))
        assertTrue(lines.any { it is ReceiptLine.Text && it.text == "A-007" && it.large })
        assertTrue(lines.any { it is ReceiptLine.Text && it.text == "Deliver to: 12 Mabini St" })
    }
}
