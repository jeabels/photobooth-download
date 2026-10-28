package com.foodpos.app

import com.foodpos.app.core.Money
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test
    fun formats() {
        assertEquals("₱1,234.50", Money.format(123_450))
        assertEquals("₱0.05", Money.format(5))
        assertEquals("-₱12.00", Money.format(-1_200))
        assertEquals("$3.10", Money.format(310, "$"))
        assertEquals("12345.50", Money.plain(1_234_550))
    }

    @Test
    fun compact() {
        assertEquals("₱950", Money.compact(95_000))
        assertEquals("₱12.5k", Money.compact(1_250_000))
        assertEquals("₱2M", Money.compact(200_000_000))
    }

    @Test
    fun parses() {
        assertEquals(125_050L, Money.parse("1,250.5"))
        assertEquals(8_000L, Money.parse("₱80"))
        assertEquals(1L, Money.parse("0.01"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("-5"))
        assertNull(Money.parse("1.005"))
    }
}
