package com.example.ui

import com.example.data.Outing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class OutingSelectionTest {
    private val format = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    @Test
    fun selectsEarliestFutureOutingByDateAndTime() {
        val now = date("2026-11-28 18:00")
        val selected = nextUpcomingOuting(
            listOf(
                outing("later", "2026-11-29", "09:00"),
                outing("earlier", "2026-11-28", "19:00"),
                outing("old", "2026-11-28", "17:59")
            ),
            now
        )

        assertEquals("earlier", selected?.id)
    }

    @Test
    fun includesOutingExactlyAtCurrentTime() {
        val selected = nextUpcomingOuting(
            listOf(outing("now", "2026-11-28", "18:00")),
            date("2026-11-28 18:00")
        )

        assertEquals("now", selected?.id)
    }

    @Test
    fun returnsNullWhenAllOutingsAreInThePastOrCancelled() {
        val selected = nextUpcomingOuting(
            listOf(
                outing("old", "2026-11-27", "23:59"),
                outing("cancelled", "2026-11-29", "09:00", status = "CANCELLED")
            ),
            date("2026-11-28 18:00")
        )

        assertNull(selected)
    }

    @Test
    fun usesCurrentDefaultTimeZoneWhenReevaluatingOutings() {
        val originalTimeZone = TimeZone.getDefault()
        try {
            val now = Date.from(Instant.parse("2026-11-28T18:30:00Z"))
            val localOuting = listOf(outing("local", "2026-11-28", "19:00"))

            TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
            val upcomingInUtc = nextUpcomingOuting(localOuting, now)

            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            val pastInTokyo = nextUpcomingOuting(localOuting, now)

            assertEquals("local", upcomingInUtc?.id)
            assertNull(pastInTokyo)
        } finally {
            TimeZone.setDefault(originalTimeZone)
        }
    }

    private fun outing(id: String, dateIso: String, time: String, status: String = "CONFIRMED") = Outing(
        id = id,
        title = id,
        dateIso = dateIso,
        time = time,
        status = status
    )

    private fun date(value: String) = format.parse(value)!!
}
