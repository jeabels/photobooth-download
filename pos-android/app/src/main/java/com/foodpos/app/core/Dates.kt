package com.foodpos.app.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Date helpers. Timestamps are epoch millis; calendar dates (expiry, expense date) are epoch days. */
object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()

    private val dateFormat = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)
    private val dateTimeFormat = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a", Locale.US)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

    fun today(): LocalDate = LocalDate.now(zone)

    fun toLocalDate(millis: Long, zone: ZoneId = this.zone): LocalDate =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    fun startOfDay(date: LocalDate, zone: ZoneId = this.zone): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    fun formatDate(date: LocalDate): String = date.format(dateFormat)
    fun formatEpochDay(epochDay: Long): String = formatDate(LocalDate.ofEpochDay(epochDay))
    fun formatDateTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(dateTimeFormat)
    fun formatTime(millis: Long): String = Instant.ofEpochMilli(millis).atZone(zone).format(timeFormat)

    /** Material 3 date pickers work in UTC midnight millis. */
    fun fromPickerMillis(utcMillis: Long): LocalDate =
        Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

    fun toPickerMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}
