package com.hank.musicfree.feature.settings.traffic

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class TrafficScopeShiftTest {
    private val base = LocalDate.of(2026, 5, 19)

    @Test fun day_shift_plus_one() {
        assertEquals(LocalDate.of(2026, 5, 20), TrafficScope.DAY.shift(base, 1))
    }

    @Test fun month_shift_minus_two() {
        assertEquals(LocalDate.of(2026, 3, 19), TrafficScope.MONTH.shift(base, -2))
    }

    @Test fun year_shift_crosses_year() {
        assertEquals(LocalDate.of(2027, 5, 19), TrafficScope.YEAR.shift(base, 1))
    }

    @Test fun total_shift_no_op() {
        assertEquals(base, TrafficScope.TOTAL.shift(base, 5))
    }

    @Test fun week_normalize_to_monday() {
        assertEquals(LocalDate.of(2026, 5, 18), TrafficScope.WEEK.normalize(base))
    }
}
