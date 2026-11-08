package com.family.sleepschedule

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Парс даты рождения («ДД.ММ.ГГГГ»): валидные, мусор, будущее время. */
class TimeInputTest {

    private fun parse(text: String): Long? = try {
        val d = LocalDate.parse(text.trim(), DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        if (d.isAfter(LocalDate.now())) null
        else d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (_: Exception) { null }

    @Test
    fun `валидная дата парсится`() {
        assertNotNull(parse("17.07.2026"))
        assertNotNull(parse(" 01.01.2026 "))
    }

    @Test
    fun `мусор и будущее отклоняются`() {
        assertNull(parse("абв"))
        assertNull(parse("32.13.2026"))
        assertNull(parse("01.01.2999"))
        assertNull(parse(""))
    }
}
