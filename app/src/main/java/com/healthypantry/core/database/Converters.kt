package com.healthypantry.core.database

import androidx.room.TypeConverter
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import java.time.Instant
import java.time.LocalDate

/**
 * Room [androidx.room.TypeConverters] for the app's non-primitive column types. Enums are
 * stored by name (stable across reorderings, unlike ordinal); dates/instants are stored as
 * ISO-8601 text / epoch millis respectively.
 */
class Converters {

    @TypeConverter
    fun fromMeasurementUnit(unit: MeasurementUnit): String = unit.name

    @TypeConverter
    fun toMeasurementUnit(value: String): MeasurementUnit = MeasurementUnit.valueOf(value)

    @TypeConverter
    fun fromFoodItemSource(source: FoodItemSource): String = source.name

    @TypeConverter
    fun toFoodItemSource(value: String): FoodItemSource = FoodItemSource.valueOf(value)

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): String? = date?.toString()

    @TypeConverter
    fun toLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun fromInstant(instant: Instant): Long = instant.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long): Instant = Instant.ofEpochMilli(value)
}
