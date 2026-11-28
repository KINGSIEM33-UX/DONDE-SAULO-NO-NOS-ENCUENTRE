package com.example.ui

import com.example.data.Outing
import com.example.data.OutingStatus
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
    val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply {
        isLenient = false
        timeZone = TimeZone.getDefault()
    }
    val parsed = formatter.parse(value, position)
    return parsed?.takeIf { position.index == value.length }
}
