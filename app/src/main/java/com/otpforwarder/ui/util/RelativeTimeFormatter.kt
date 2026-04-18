package com.otpforwarder.ui.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

fun formatRelativeTime(instant: Instant, now: Instant = Instant.now()): String {
    val seconds = ChronoUnit.SECONDS.between(instant, now)
    if (abs(seconds) < 60) return "just now"
    val minutes = ChronoUnit.MINUTES.between(instant, now)
    if (abs(minutes) < 60) return "${minutes}m ago"
    val hours = ChronoUnit.HOURS.between(instant, now)
    if (abs(hours) < 24) return "${hours}h ago"
    val days = ChronoUnit.DAYS.between(instant, now)
    return "${days}d ago"
}

enum class DayBucket { TODAY, YESTERDAY, OLDER }

fun dayBucket(instant: Instant, zoneId: ZoneId = ZoneId.systemDefault()): DayBucket {
    val date = instant.atZone(zoneId).toLocalDate()
    val today = LocalDate.now(zoneId)
    return when (date) {
        today -> DayBucket.TODAY
        today.minusDays(1) -> DayBucket.YESTERDAY
        else -> DayBucket.OLDER
    }
}

fun DayBucket.label(): String = when (this) {
    DayBucket.TODAY -> "Today"
    DayBucket.YESTERDAY -> "Yesterday"
    DayBucket.OLDER -> "Earlier"
}
