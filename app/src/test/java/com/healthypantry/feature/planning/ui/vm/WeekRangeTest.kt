package com.healthypantry.feature.planning.ui.vm

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Spec: Weekly Plan Assignment and Quick-Add, Weekly Ingredient Needs
 * (openspec/changes/pantry-tracker/specs/meal-planning/spec.md).
 *
 * [currentWeekRange] must always resolve to the Monday-Sunday week containing [today], regardless
 * of which day of that week [today] itself falls on.
 */
class WeekRangeTest {

    @Test
    fun `a mid-week today resolves to that week's Monday-Sunday boundaries`() {
        // GIVEN a Wednesday
        val wednesday = LocalDate.of(2026, 7, 8)
        check(wednesday.dayOfWeek == DayOfWeek.WEDNESDAY)

        val range = currentWeekRange(wednesday)

        // THEN the range starts on that week's Monday and ends on that week's Sunday
        val expectedMonday = LocalDate.of(2026, 7, 6)
        val expectedSunday = LocalDate.of(2026, 7, 12)
        assertEquals(expectedMonday.toEpochDay(), range.startEpochDay)
        assertEquals(expectedSunday.toEpochDay(), range.endEpochDay)
    }

    @Test
    fun `a Monday today returns itself as the start of the range, not the previous week`() {
        val monday = LocalDate.of(2026, 7, 6)
        check(monday.dayOfWeek == DayOfWeek.MONDAY)

        val range = currentWeekRange(monday)

        assertEquals(monday.toEpochDay(), range.startEpochDay)
        assertEquals(monday.plusDays(6).toEpochDay(), range.endEpochDay)
    }

    @Test
    fun `a Sunday today stays within its own week and does not roll into the next week's Monday`() {
        val sunday = LocalDate.of(2026, 7, 12)
        check(sunday.dayOfWeek == DayOfWeek.SUNDAY)

        val range = currentWeekRange(sunday)

        // THEN the range's end is this Sunday itself (not the next week's), and the start is the
        // Monday that precedes it, six days earlier - never a start/end that lands the following
        // week's Monday inside this range
        val expectedMonday = LocalDate.of(2026, 7, 6)
        assertEquals(expectedMonday.toEpochDay(), range.startEpochDay)
        assertEquals(sunday.toEpochDay(), range.endEpochDay)
        assertEquals(6, range.endEpochDay - range.startEpochDay)
    }
}
