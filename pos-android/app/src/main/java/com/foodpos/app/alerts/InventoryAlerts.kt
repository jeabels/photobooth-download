package com.foodpos.app.alerts

enum class AlertType(val label: String) {
    OUT_OF_STOCK("Out of Stock"),
    SPOILED("Spoiled"),
    SPOILING_SOON("Spoiling Soon"),
    RUNNING_LOW("Running Low"),
}

data class InventoryAlert(
    val type: AlertType,
    val productId: String,
    val productName: String,
    val batchId: String? = null,
    val batchNumber: String? = null,
    val detail: String,
) {
    /** Stable identity, used to tell new alerts from ones already shown. */
    val key: String get() = "${type.name}:$productId:${batchId.orEmpty()}"
}

/** Turns stock levels and batch freshness into the alert list shown in the app and in notifications. */
object InventoryAlerts {
    fun compute(stock: List<ProductStock>): List<InventoryAlert> {
        val alerts = mutableListOf<InventoryAlert>()
        for (item in stock) {
            val p = item.product
            when (item.level) {
                StockLevel.OUT_OF_STOCK -> alerts += InventoryAlert(
                    type = AlertType.OUT_OF_STOCK,
                    productId = p.id,
                    productName = p.name,
                    detail = "No sellable ${p.unit} left",
                )
                StockLevel.RUNNING_LOW -> alerts += InventoryAlert(
                    type = AlertType.RUNNING_LOW,
                    productId = p.id,
                    productName = p.name,
                    detail = "${item.sellable} ${p.unit} left (alert at ${p.lowStockThreshold})",
                )
                StockLevel.IN_STOCK -> Unit
            }
            for (batch in item.batches) {
                val daysLeft = batch.daysLeft ?: continue
                when (batch.status) {
                    BatchStatus.SPOILED -> alerts += InventoryAlert(
                        type = AlertType.SPOILED,
                        productId = p.id,
                        productName = p.name,
                        batchId = batch.batch.id,
                        batchNumber = batch.batch.batchNumber,
                        detail = "${batch.remaining} ${p.unit} expired ${-daysLeft} day(s) ago",
                    )
                    BatchStatus.SPOILING_SOON -> alerts += InventoryAlert(
                        type = AlertType.SPOILING_SOON,
                        productId = p.id,
                        productName = p.name,
                        batchId = batch.batch.id,
                        batchNumber = batch.batch.batchNumber,
                        detail = when (daysLeft) {
                            0L -> "${batch.remaining} ${p.unit} expire today"
                            1L -> "${batch.remaining} ${p.unit} expire tomorrow"
                            else -> "${batch.remaining} ${p.unit} expire in $daysLeft days"
                        },
                    )
                    else -> Unit
                }
            }
        }
        return alerts.sortedWith(compareBy({ it.type.ordinal }, { it.productName.lowercase() }))
    }

    fun summary(alerts: List<InventoryAlert>): String =
        alerts.groupingBy { it.type }.eachCount().entries
            .sortedBy { it.key.ordinal }
            .joinToString(" · ") { "${it.value} ${it.key.label}" }
}
