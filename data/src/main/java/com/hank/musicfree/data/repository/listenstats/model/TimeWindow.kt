package com.hank.musicfree.data.repository.listenstats.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class TimeWindow(val startMs: Long, val endMs: Long, val label: String)

fun windowFor(
    scope: TimeScope,
    anchor: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    firstEventDate: LocalDate? = null,
): TimeWindow {
    val (startDate, endDateExclusive, label) = when (scope) {
        TimeScope.DAY -> Triple(
            anchor, anchor.plusDays(1),
            "${anchor.year} 年 ${anchor.monthValue} 月 ${anchor.dayOfMonth} 日",
        )
        TimeScope.WEEK -> {
            val monday = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sundayNext = monday.plusDays(7)
            val sunday = monday.plusDays(6)
            Triple(
                monday, sundayNext,
                "${monday.monthValue}/${monday.dayOfMonth} – ${sunday.monthValue}/${sunday.dayOfMonth}",
            )
        }
        TimeScope.MONTH -> {
            val first = anchor.withDayOfMonth(1)
            Triple(first, first.plusMonths(1), "${first.year} 年 ${first.monthValue} 月")
        }
        TimeScope.YEAR -> {
            val first = anchor.withDayOfYear(1)
            Triple(first, first.plusYears(1), "${first.year} 年")
        }
        TimeScope.ALL_TIME -> {
            val first = firstEventDate ?: LocalDate.ofEpochDay(0)
            Triple(first, anchor.plusDays(1), "总计")
        }
    }
    return TimeWindow(
        startMs = startDate.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMs = endDateExclusive.atStartOfDay(zone).toInstant().toEpochMilli(),
        label = label,
    )
}
