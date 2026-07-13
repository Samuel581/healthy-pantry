package com.healthypantry.feature.planning.ui.vm

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Inclusive Monday-to-Sunday range of [PlanEntry.dateEpochDay][com.healthypantry.feature.planning.domain.model.PlanEntry.dateEpochDay]
 * values for `PlanViewModel.uiState`'s week view (spec "Weekly Plan Assignment and Quick-Add",
 * "Weekly Ingredient Needs"). A UI-layer concept only — the planning domain use-cases
 * (`ComputeWeeklyNeedsUseCase`) accept an already-resolved list of entries and don't care which
 * week produced it.
 */
data class WeekRange(val startEpochDay: Long, val endEpochDay: Long) {
    /** Every day in the range, Monday first. */
    val days: List<Long> get() = (startEpochDay..endEpochDay).toList()
}

/** The Monday-to-Sunday week containing [today] (defaults to the real current date). */
fun currentWeekRange(today: LocalDate = LocalDate.now()): WeekRange {
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6)
    return WeekRange(startEpochDay = monday.toEpochDay(), endEpochDay = sunday.toEpochDay())
}
