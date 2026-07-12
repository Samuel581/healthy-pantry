# Tasks: Pantry Tracker (v1 core)

> Reconstructed 2026-07-12 after the Engram MCP backend disconnected mid-chain, from the
> full content last read from Engram (topic_key `sdd/pantry-tracker/tasks`, observation #9)
> earlier in this conversation. Content below is verbatim from that read, with Phase 1
> marked complete to reflect PR1 having landed.

## Review Workload Forecast

| Field | Value |
|---|---|
| Estimated changed lines | ~3800-4600 total, 150-500/PR |
| 400-line budget risk | High |
| Chained PRs recommended | Yes (originally suggested feature-branch-chain) |
| Chain strategy (as actually decided) | **Serial delivery** — each branch cut from `dev` only after the previous PR merged to `dev`; every PR targets `dev` directly (user's explicit convention overrides the agent's original stacked-chain suggestion) |
| Delivery strategy | ask-on-risk |

Decision needed before apply: Resolved — serial delivery confirmed by user.

Every implementation task is RED (failing test) then GREEN (make it pass) per Strict TDD.

### Suggested Work Units

| Unit | Goal | PR | Focused test command | Runtime harness | Rollback boundary |
|---|---|---|---|---|---|
|1|Scaffold Gradle/AGP/Kotlin/Compose/Hilt/Room/WorkManager|PR1|`./gradlew build`|Empty app launches on emulator|revert app/, gradle/, build files|
|2|core/unit: MeasurementUnit, UnitConverter|PR2|`./gradlew test --tests UnitConverterTest`|N/A, pure JVM|revert core/unit/|
|3|pantry-stock data: FoodItem/UnitConversion/StockBatch|PR3|`./gradlew testDebugUnitTest --tests *pantry.data*`|Robolectric in-memory Room|revert feature/pantry/data/|
|4|pantry-stock domain: projected/actual stock|PR4|`./gradlew test --tests *ProjectedStock*`|N/A, pure JVM|revert feature/pantry/domain/|
|5|nutrition-lookup: OFF+USDA, fallback/override|PR5|`./gradlew test --tests *NutritionLookup*`|Fake HTTP (MockWebServer)|revert feature/nutrition/|
|6|pantry-stock UI: list, add/edit, barcode scan|PR6|`./gradlew connectedDebugAndroidTest --tests *PantryScreen*`|Compose UI test on emulator|revert feature/pantry/ui/|
|7|meal-planning data: Recipe/Ingredient/PlanEntry|PR7|`./gradlew testDebugUnitTest --tests *planning.data*`|Robolectric in-memory Room|revert feature/planning,recipes data|
|8|planning+macro domain: weekly needs, macro rollup|PR8|`./gradlew test --tests *WeeklyNeeds*,*MacroTotals*`|N/A, pure JVM|revert feature/planning/domain/|
|9|meal-planning UI: recipe CRUD, weekly plan|PR9|`./gradlew connectedDebugAndroidTest --tests *PlanScreen*`|Compose UI test on emulator|revert feature/planning,recipes ui|
|10|expiry-reminders: worker, notification, fallback|PR10|`./gradlew test --tests *ExpiryCheckWorker*`|WorkManager TestDriver|revert feature/expiry/|
|11|Integration: nav, bottom bar, theme, README|PR11|`./gradlew connectedDebugAndroidTest`|Full app smoke on emulator|revert app/navigation, README|

## Phase 1: Scaffolding (PR1) — ✅ COMPLETE
- [x] 1.1 Wire `gradle/libs.versions.toml` (AGP/Kotlin K2/Compose/Room-KSP/Hilt/WorkManager/code-scanner/Retrofit) + `app/build.gradle.kts` (minSdk26/compile-target36/JDK17).
- [x] 1.2 Create package skeleton (app/core/feature/*), `HealthyPantryApp`, blank `MainActivity`+Scaffold, `USDA_FDC_API_KEY` BuildConfig from local.properties.

Landed as commit `d9de8c1` on branch `chore/scaffold-project` → merged to `dev`.

## Phase 2: Core Domain (PR2) — ✅ COMPLETE
- [x] 2.1 RED/GREEN `UnitConverterTest`/`UnitConverter` + `MeasurementUnit` enum (spec: Unit Conversion Correctness).
- [x] 2.2 `core/common` Result/DispatcherProvider; `core/network` Retrofit/OkHttp Hilt module.

## Phase 3: Pantry Data (PR3)

> Split into PR3a (entities/DAOs) and PR3b (repositories) — PR3 came in at 1209 lines,
> over the 400-line review budget; user chose to split rather than accept as exception.

- [x] 3.1 RED/GREEN Robolectric DAO tests + `FoodItem`/`UnitConversion`/`StockBatch` entities, DAOs, `AppDatabase` v1 (spec: Item and Stock Batch CRUD). — PR3a, branch `feat/pantry-entities-dao`
- [x] 3.2 `FoodItemRepository`, `StockBatchRepository`. — PR3b, branch `feat/pantry-repositories`

## Phase 4: Pantry Domain (PR4) — ✅ COMPLETE
- [x] 4.1 RED/GREEN `ComputeProjectedStockUseCase` (spec: Projected vs Actual Stock).

## Phase 5: Nutrition Lookup (PR5) — ✅ COMPLETE

> Split in progress to stay under the ~400-450 line review budget (see PR3 note above for why
> this project now splits proactively instead of after the fact).

- [x] 5.1 RED/GREEN `NutritionLookupRepository`, OFF/USDA Retrofit sources, fallback+manualOverride (spec: Barcode Scan, Manual Entry with USDA Fallback).
  - [x] 5.1a Open Food Facts client: `OpenFoodFactsApi` Retrofit interface, `OffProductResponse`/`OffProduct`/`OffNutriments` DTOs, `OpenFoodFactsNutritionSource` (DTO -> shared `NutritionResult` mapping, barcode-miss -> `NutritionLookupError.NotFound`, no auto-USDA-guess per spec scenario "Barcode not found in Open Food Facts"), `NutritionNetworkModule` Hilt wiring, RED/GREEN MockWebServer tests (`OpenFoodFactsNutritionSourceTest`, spec scenario "Successful barcode scan and OFF lookup"). Also added shared `NutritionResult`/`NutritionSource`/`NutritionLookupError` domain types (`feature/nutrition/domain/model`) and the `mockwebserver` test dependency. Landed on branch `feat/nutrition-lookup` — diff ~272 lines.
  - [x] 5.1b USDA FoodData Central client: `UsdaFoodDataCentralApi` Retrofit interface (`GET /v1/foods/search`) + `UsdaSearchResponse`/`UsdaFood`/`UsdaFoodNutrient` DTOs in `feature/nutrition/data/usda`, reading `BuildConfig.USDA_FDC_API_KEY`; `UsdaNutritionSource.searchByName` mapping the first match to `NutritionResult` (macro fields mapped through as null when absent, not defaulted to 0.0, matching the OFF fix). Distinguishes HTTP 429 -> `NutritionLookupError.RateLimited` from an empty `foods` list -> `NotFound` (spec scenario "USDA API key not configured"), other non-2xx -> `ApiError`, transport failures -> `NetworkError`; covers manual name-search (spec scenario "Manual name search against USDA FoodData Central"). RED/GREEN MockWebServer tests (`UsdaNutritionSourceTest`, 6 cases: match, miss, 429 rate-limit, network error, other non-2xx, missing-macro-field null-mapping). No Hilt DI wiring added yet — deferred to 5.1c, which will provide `UsdaFoodDataCentralApi` via `NutritionNetworkModule` alongside the composing repository. Landed on branch `feat/nutrition-usda` — diff ~230 lines.
  - [x] 5.1c `NutritionLookupRepository` interface + `NutritionLookupRepositoryImpl` in `feature/nutrition/data` composing OFF (barcode) and USDA (manual name-search) sources per the fallback rule above (thin pass-through/delegation, no duplicated mapping/error logic); `NutritionRepositoryModule` Hilt `@Binds` module (`feature/nutrition/data/di`, same pattern as `PantryRepositoryModule`); `NutritionNetworkModule` now also provides `UsdaFoodDataCentralApi` (base URL `https://api.nal.usda.gov/fdc/`). RED/GREEN tests (`NutritionLookupRepositoryTest`, 3 cases: barcode hit delegates to OFF unchanged, barcode miss surfaces as-is without auto-querying USDA, name-search delegates to USDA) using hand-written fake `OpenFoodFactsApi`/`UsdaFoodDataCentralApi` wrapped in the real source classes (same hand-written-fake convention as `ComputeProjectedStockUseCaseTest`). Landed on branch `feat/nutrition-lookup-repository` — diff ~250 lines.
  - Manual-override precedence (spec scenario "Manual macro override always wins") is a `FoodItem`/form concern, not the repository's — it belongs in PR6 (`ItemFormScreen`/`PantryViewModel`), not here. Documented as an explicit doc-comment note on `NutritionLookupRepositoryImpl`.

## Phase 6: Pantry UI (PR6)
- [ ] 6.1 `PantryViewModel`+tests; `PantryListScreen`, `ItemFormScreen` (Code Scanner + manual + USDA search) + compose tests.

## Phase 7: Meal-Planning Data (PR7)
- [ ] 7.1 RED/GREEN `Recipe`/`RecipeIngredient`/`PlanEntry` entities, DAOs, repos (spec: Recipe CRUD, Weekly Plan Assignment).

## Phase 8: Planning + Macro Domain (PR8)
- [ ] 8.1 RED/GREEN `ComputeWeeklyNeedsUseCase`, `ComputeMacroTotalsUseCase`, mark-eaten use-case (spec: Item-to-Day Macro Rollup).

## Phase 9: Planning UI (PR9)
- [ ] 9.1 `RecipeViewModel`+`RecipeScreen`; `PlanViewModel`+`WeekPlanScreen` (assign/quick-add/mark-eaten) + compose tests.

## Phase 10: Expiry Reminders (PR10)
- [ ] 10.1 RED/GREEN `ExpiryCheckWorker` (WorkManager TestDriver), notification channel, POST_NOTIFICATIONS request, `ExpiryAlertRepository`, in-app banner/badge (spec: Expiry Notification Scheduling).

## Phase 11: Integration (PR11)
- [ ] 11.1 Nav graph + bottom nav, theme, schedule periodic worker; e2e smoke test; README USDA key docs.
