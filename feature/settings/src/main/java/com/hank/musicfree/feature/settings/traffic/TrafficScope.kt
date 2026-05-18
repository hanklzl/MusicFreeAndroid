package com.hank.musicfree.feature.settings.traffic

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class TrafficScope { DAY, WEEK, MONTH, YEAR, TOTAL }

fun TrafficScope.shift(anchor: LocalDate, direction: Int): LocalDate = when (this) {
    TrafficScope.DAY -> anchor.plusDays(direction.toLong())
    TrafficScope.WEEK -> anchor.plusWeeks(direction.toLong())
    TrafficScope.MONTH -> anchor.plusMonths(direction.toLong())
    TrafficScope.YEAR -> anchor.plusYears(direction.toLong())
    TrafficScope.TOTAL -> anchor
}

fun TrafficScope.normalize(anchor: LocalDate): LocalDate = when (this) {
    TrafficScope.DAY -> anchor
    TrafficScope.WEEK -> anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    TrafficScope.MONTH -> anchor.withDayOfMonth(1)
    TrafficScope.YEAR -> anchor.withDayOfYear(1)
    TrafficScope.TOTAL -> LocalDate.now()
}
