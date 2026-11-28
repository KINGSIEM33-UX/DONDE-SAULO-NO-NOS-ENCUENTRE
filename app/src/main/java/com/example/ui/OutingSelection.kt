package com.example.ui

import com.example.data.Outing
import com.example.data.OutingStatus
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val OUTING_DATE_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
    isLenient = false
}

/** Returns the earliest non-cancelled outing at or after [now]. */
fun nextUpcomingOuting(outings: List<Outing>, now: Date = Date()): Outing? {
    return outings
        .asSequence()
        .filter { it.outingStatus != OutingStatus.CANCELLED }
        .mapNotNull { outing ->
            parseOutingDateTime(outing)?.let { parsed -> parsed to outing }
        }
        .filter { (dateTime, _) -> !dateTime.before(now) }
        .minByOrNull { (dateTime, _) -> dateTime.time }
        ?.second
}

private fun parseOutingDateTime(outing: Outing): Date? {
    val value = "${outing.dateIso} ${outing.time}"
    val position = ParsePosition(0)
    val parsed = synchronized(OUTING_DATE_TIME_FORMAT) {
        OUTING_DATE_TIME_FORMAT.parse(value, position)
    }
    return parsed?.takeIf { position.index == value.length }
}
