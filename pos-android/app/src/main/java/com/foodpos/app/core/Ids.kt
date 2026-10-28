package com.foodpos.app.core

import java.util.UUID

/**
 * Rows use random UUIDs instead of auto-increment ids, so records created offline on different
 * devices never collide when they meet in the cloud.
 */
fun newId(): String = UUID.randomUUID().toString()
