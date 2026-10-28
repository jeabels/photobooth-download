package com.foodpos.app.printer

import com.foodpos.app.core.Dates
import com.foodpos.app.core.Money
import com.foodpos.app.data.local.OrderStatus
import com.foodpos.app.data.local.OrderType
import com.foodpos.app.data.local.OrderWithItems
import com.foodpos.app.data.local.PaymentMethod
import com.foodpos.app.data.repository.AppSettings

/** Lays out the customer receipt for an order. Pure Kotlin, so it is unit-tested without a printer. */
object ReceiptFormatter {
    fun build(
        order: OrderWithItems,
        settings: AppSettings,
        logo: MonoRaster?,
        printedAt: String = Dates.formatDateTime(order.order.createdAt),
    ): Receipt {
        val o = order.order
        val lines = mutableListOf<ReceiptLine>()
        fun money(cents: Long) = Money.plain(cents)

        if (logo != null) lines += ReceiptLine.Logo(logo)
        lines += ReceiptLine.Text(settings.businessName, ReceiptAlign.CENTER, bold = true, large = true)
        if (settings.businessAddress.isNotBlank()) lines += ReceiptLine.Text(settings.businessAddress, ReceiptAlign.CENTER)
        if (settings.businessPhone.isNotBlank()) lines += ReceiptLine.Text(settings.businessPhone, ReceiptAlign.CENTER)
        lines += ReceiptLine.Divider

        lines += ReceiptLine.Text("QUEUE NO.", ReceiptAlign.CENTER)
        lines += ReceiptLine.Text(o.queueNumber, ReceiptAlign.CENTER, bold = true, large = true)
        if (o.status == OrderStatus.VOIDED) lines += ReceiptLine.Text("*** VOIDED ***", ReceiptAlign.CENTER, bold = true)
        lines += ReceiptLine.Divider

        lines += ReceiptLine.Text(printedAt)
        lines += ReceiptLine.Text("Cashier: ${o.cashierName}")
        lines += ReceiptLine.Text("Order: ${o.orderType.label}")
        lines += ReceiptLine.Divider

        for (item in order.items) {
            lines += ReceiptLine.Text(item.productName)
            lines += ReceiptLine.Columns("  ${item.quantity} x ${money(item.unitPriceCents)}", money(item.lineTotalCents))
        }
        lines += ReceiptLine.Divider

        lines += ReceiptLine.Columns("Subtotal", money(o.subtotalCents))
        if (o.orderType == OrderType.DELIVERY) lines += ReceiptLine.Columns("Delivery fee", money(o.deliveryFeeCents))
        lines += ReceiptLine.Columns("TOTAL", "${settings.currencySymbol}${money(o.totalCents)}", bold = true, tall = true)
        lines += ReceiptLine.Columns("Paid by", o.paymentMethod.label)
        if (o.paymentMethod == PaymentMethod.CASH && o.amountTenderedCents != null) {
            lines += ReceiptLine.Columns("Cash", money(o.amountTenderedCents))
            lines += ReceiptLine.Columns("Change", money(o.changeCents), bold = true)
        }
        if (o.paymentReference.isNotBlank()) lines += ReceiptLine.Columns("Ref no.", o.paymentReference)

        if (o.customerName.isNotBlank() || o.customerPhone.isNotBlank() || o.customerAddress.isNotBlank()) {
            lines += ReceiptLine.Divider
            if (o.customerName.isNotBlank()) lines += ReceiptLine.Text("Customer: ${o.customerName}")
            if (o.customerPhone.isNotBlank()) lines += ReceiptLine.Text("Phone: ${o.customerPhone}")
            if (o.customerAddress.isNotBlank()) lines += ReceiptLine.Text("Deliver to: ${o.customerAddress}")
        }
        if (o.notes.isNotBlank()) lines += ReceiptLine.Text("Notes: ${o.notes}")

        if (settings.receiptFooter.isNotBlank()) {
            lines += ReceiptLine.Divider
            lines += ReceiptLine.Text(settings.receiptFooter, ReceiptAlign.CENTER)
        }
        return Receipt(lines)
    }

    fun testPage(settings: AppSettings, logo: MonoRaster?): Receipt = Receipt(
        buildList {
            if (logo != null) add(ReceiptLine.Logo(logo))
            add(ReceiptLine.Text(settings.businessName, ReceiptAlign.CENTER, bold = true, large = true))
            add(ReceiptLine.Text("Printer test", ReceiptAlign.CENTER))
            add(ReceiptLine.Divider)
            add(ReceiptLine.Columns("Normal text", "1.00"))
            add(ReceiptLine.Columns("Bold total", "${settings.currencySymbol}99.00", bold = true, tall = true))
            add(ReceiptLine.Text("Paper: ${settings.paperWidthMm} mm, ${settings.charsPerLine} characters", ReceiptAlign.CENTER))
        },
    )
}
