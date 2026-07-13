package com.healthypantry.feature.planning.data.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.healthypantry.core.database.AppDatabase
import com.healthypantry.core.unit.MeasurementUnit
import com.healthypantry.feature.pantry.data.dao.FoodItemDao
import com.healthypantry.feature.pantry.data.entity.FoodItemEntity
import com.healthypantry.feature.pantry.domain.model.FoodItemSource
import com.healthypantry.feature.planning.data.entity.PlanEntryEntity
import com.healthypantry.feature.planning.domain.model.MealSlot
import com.healthypantry.feature.planning.domain.model.PlanEntryType
import com.healthypantry.feature.recipes.data.dao.RecipeDao
import com.healthypantry.feature.recipes.data.entity.RecipeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * Spec: Weekly Plan Assignment and Quick-Add
 * (sdd/pantry-tracker/spec, domain meal-planning)
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlanEntryDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: PlanEntryDao
    private var bowlId: Long = 0
    private var bananaId: Long = 0

    private val monday = 20000L
    private val tuesday = 20001L
    private val nextMonday = 20007L

    @Before
    fun setUp() = runBlocking {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = database.planEntryDao()

        val recipeDao: RecipeDao = database.recipeDao()
        val foodItemDao: FoodItemDao = database.foodItemDao()

        bowlId = recipeDao.insert(
            RecipeEntity(name = "Bowl", servings = 2, notes = null, createdAt = Instant.parse("2026-07-12T00:00:00Z")),
        )
        bananaId = foodItemDao.insert(
            FoodItemEntity(
                name = "Banana",
                canonicalUnit = MeasurementUnit.PIECE,
                source = FoodItemSource.MANUAL,
                caloriesPerUnit = 89.0,
                proteinGramsPerUnit = 1.1,
                carbsGramsPerUnit = 22.8,
                fatGramsPerUnit = 0.3,
                barcode = null,
                externalSourceId = null,
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun recipeAssignment(day: Long, slot: MealSlot) = PlanEntryEntity(
        dateEpochDay = day,
        mealSlot = slot,
        type = PlanEntryType.RECIPE,
        recipeId = bowlId,
        foodItemId = null,
        quantity = null,
        servings = 1.0,
        eaten = false,
        eatenAt = null,
    )

    private fun itemQuickAdd(day: Long, slot: MealSlot) = PlanEntryEntity(
        dateEpochDay = day,
        mealSlot = slot,
        type = PlanEntryType.ITEM,
        recipeId = null,
        foodItemId = bananaId,
        quantity = 1.0,
        servings = null,
        eaten = false,
        eatenAt = null,
    )

    @Test
    fun `assigning a recipe to a day-slot persists a RECIPE entry`() = runBlocking {
        // GIVEN recipe "Bowl" exists (see setUp)
        // WHEN assigned to Monday/dinner
        val id = dao.insert(recipeAssignment(monday, MealSlot.DINNER))

        // THEN Monday/dinner shows "Bowl"
        val stored = dao.observeWeek(monday, monday).first().first { it.id == id }
        assertEquals(PlanEntryType.RECIPE, stored.type)
        assertEquals(bowlId, stored.recipeId)
        assertEquals(MealSlot.DINNER, stored.mealSlot)
    }

    @Test
    fun `quick-adding a single item to a day-slot persists an ITEM entry without a recipe`() = runBlocking {
        // GIVEN item "Banana" exists (see setUp)
        // WHEN quick-added to Tuesday/breakfast without a recipe
        val id = dao.insert(itemQuickAdd(tuesday, MealSlot.BREAKFAST))

        // THEN Tuesday/breakfast shows the item entry
        val stored = dao.observeWeek(tuesday, tuesday).first().first { it.id == id }
        assertEquals(PlanEntryType.ITEM, stored.type)
        assertEquals(bananaId, stored.foodItemId)
        assertNull(stored.recipeId)
        assertEquals(1.0, stored.quantity ?: -1.0, 0.0001)
    }

    @Test
    fun `observeWeek returns only entries within the given date range`() = runBlocking {
        dao.insert(recipeAssignment(monday, MealSlot.DINNER))
        dao.insert(itemQuickAdd(tuesday, MealSlot.BREAKFAST))
        dao.insert(recipeAssignment(nextMonday, MealSlot.LUNCH))

        val week = dao.observeWeek(monday, monday + 6).first()

        assertEquals(2, week.size)
        assertTrue(week.none { it.dateEpochDay == nextMonday })
    }

    @Test
    fun `markEaten sets eaten true and the given eatenAt, leaving other entries untouched`() = runBlocking {
        val eatenId = dao.insert(recipeAssignment(monday, MealSlot.DINNER))
        val untouchedId = dao.insert(itemQuickAdd(tuesday, MealSlot.BREAKFAST))
        val eatenAtMillis = Instant.parse("2026-07-13T19:00:00Z").toEpochMilli()

        val affectedRows = dao.markEaten(eatenId, eatenAtMillis)

        val eaten = dao.observeWeek(monday, tuesday).first().first { it.id == eatenId }
        val untouched = dao.observeWeek(monday, tuesday).first().first { it.id == untouchedId }
        assertEquals(1, affectedRows)
        assertTrue(eaten.eaten)
        assertEquals(eatenAtMillis, eaten.eatenAt)
        assertNotNull(untouched)
        assertEquals(false, untouched.eaten)
        assertNull(untouched.eatenAt)
    }

    @Test
    fun `markEaten on an already-eaten entry affects zero rows and does not overwrite eatenAt`() = runBlocking {
        // GIVEN an entry already marked eaten once (the atomic guard this proves: two concurrent
        // UPDATEs for the same id can never both affect a row, since SQLite/Room serializes
        // writers - see MarkPlanEntryEatenUseCase's reliance on this return value)
        val id = dao.insert(recipeAssignment(monday, MealSlot.DINNER))
        val firstEatenAtMillis = Instant.parse("2026-07-13T19:00:00Z").toEpochMilli()
        val firstAffectedRows = dao.markEaten(id, firstEatenAtMillis)

        // WHEN markEaten is called again for the same id (double-tap / concurrent call)
        val secondEatenAtMillis = Instant.parse("2026-07-13T20:00:00Z").toEpochMilli()
        val secondAffectedRows = dao.markEaten(id, secondEatenAtMillis)

        // THEN the first call affected exactly one row, the second affected zero, and the
        // original eatenAt from the first call is left untouched
        assertEquals(1, firstAffectedRows)
        assertEquals(0, secondAffectedRows)
        val stored = dao.observeWeek(monday, monday).first().first { it.id == id }
        assertTrue(stored.eaten)
        assertEquals(firstEatenAtMillis, stored.eatenAt)
    }

    @Test
    fun `delete removes the plan entry`() = runBlocking {
        val id = dao.insert(recipeAssignment(monday, MealSlot.DINNER))
        val stored = dao.observeWeek(monday, monday).first().first { it.id == id }

        dao.delete(stored)

        assertTrue(dao.observeWeek(monday, monday).first().isEmpty())
    }

    @Test
    fun `deleting a recipe still referenced by a plan entry is rejected`() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                dao.insert(recipeAssignment(monday, MealSlot.DINNER))
                database.recipeDao().delete(
                    database.recipeDao().observeById(bowlId).first()!!,
                )
            }
        }
    }

    @Test
    fun `deleting a food item still referenced by an eaten plan entry is rejected`() {
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                val id = dao.insert(itemQuickAdd(tuesday, MealSlot.BREAKFAST))
                dao.markEaten(id, Instant.parse("2026-07-13T19:00:00Z").toEpochMilli())

                database.foodItemDao().delete(
                    database.foodItemDao().observeById(bananaId).first()!!,
                )
            }
        }
    }
}
