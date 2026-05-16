package com.hank.musicfree.data.repository.listenstats.model

enum class TimeScope { DAY, WEEK, MONTH, YEAR, ALL_TIME }

fun parseTimeScope(raw: String): TimeScope =
    runCatching { TimeScope.valueOf(raw) }.getOrDefault(TimeScope.WEEK)
