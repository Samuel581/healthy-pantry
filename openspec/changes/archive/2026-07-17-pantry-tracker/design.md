# Design: Pantry Tracker (v1 core)

> Reconstructed 2026-07-12 after the Engram MCP backend disconnected mid-chain. This
> reproduces the approved design from the `sdd-design` phase's executive summary plus the
> ground truth of what PR1 actually scaffolded (which was built while Engram was still
> reachable, from the full original design doc). Some narrative detail from the original
> is lost; the concrete decisions below are accurate.

## Architecture

Single Gradle module (`app`), package-by-feature internally — not a multi-module split,
justified by app size (v1 scope) and to keep build/config overhead low. Strict layering
within each feature:

```
app/                         # HealthyPantryApp (Hilt), MainActivity, nav, theme
core/
  database/                  # AppDatabase, shared Room config
  unit/                      # MeasurementUnit, UnitConverter (pure JVM, PR2)
  common/                    # Result, DispatcherProvider
  network/                   # Retrofit/OkHttp Hilt module
feature/
  pantry/{data,domain,ui}/       # FoodItem, StockBatch, projected/actual stock
  nutrition/{data,domain,ui}/    # NutritionLookupRepository (OFF + USDA)
  planning/{data,domain,ui}/     # PlanEntry, weekly needs, mark-eaten
  recipes/{data,domain,ui}/      # Recipe, RecipeIngredient
  expiry/{data,domain,ui}/       # ExpiryCheckWorker, notifications
```

UI layer (Compose + ViewModel, `StateFlow` via `collectAsStateWithLifecycle`) → Domain
layer (use-cases for cross-cutting/accuracy-sensitive logic) → Data layer (repositories
wrapping Room DAOs + network sources). Hilt wires all layers; `hiltViewModel()` in Compose.

## Build baseline (confirmed, as landed in PR1)

- AGP 9.2.1, Kotlin 2.3.10 (K2), Compose BOM 2026.06.01
- Room 2.8.4 (KSP), Hilt 2.60.1, WorkManager 2.11.2
- `play-services-code-scanner` 16.1.0 (Google Code Scanner, no camera permission)
- Retrofit 3.0.0, OkHttp 5.4.0, kotlinx-serialization 1.11.0
- minSdk 26, compileSdk/targetSdk 36 (Android 16) — confirmed current stable July 2026
- JDK 17 toolchain
- Some AndroidX catalog entries pinned one minor below absolute-latest (`core-ktx 1.18.0`,
  `lifecycle 2.10.0`, `hilt-navigation-compose 1.3.0`) because their newer releases require
  API 37 platform tooling not available in the scaffolding environment; revisit when API 37
  tooling is available.

## Room schema (target for PR3/PR7)

- `FoodItem` — id, name, canonical unit, source (barcode/manual), macros (per canonical
  unit), barcode (nullable), USDA/OFF source id (nullable).
- `UnitConversion` — foodItemId FK, fromUnit, toUnit, factor. Enables recipes to express
  quantities in natural units while stock stays in one canonical unit per item.
- `StockBatch` — foodItemId FK, quantity (canonical unit), expiryDate (nullable), addedAt.
- `Recipe` — id, name.
- `RecipeIngredient` — recipeId FK, foodItemId FK, quantity, unit.
- `PlanEntry` — id, day, source (recipe or direct quick-add item), recipeId (nullable),
  foodItemId (nullable, for quick-add), quantity (for quick-add), eaten (bool), eatenAt
  (nullable).

DAOs expose Room `@Relation` queries as Kotlin `Flow` for reactive UI. `AppDatabase` is
versioned starting at v1 (PR3); PR7 adds a migration for the planning/recipes tables.

## Nutrition lookup composition

`NutritionLookupRepository` interface, two backing sources:

1. **Open Food Facts** — tried first for barcode lookups (free, no key, broad packaged-food
   coverage).
2. **USDA FoodData Central** — fallback for barcode misses, and primary for manual
   name-search (raw/unpackaged ingredients OFF doesn't cover well). Requires an API key,
   provisioned via `local.properties` → `BuildConfig.USDA_FDC_API_KEY`, defaults to
   `DEMO_KEY` for a working clean checkout (low rate limit — must be swapped for real use;
   documented in README).

Manual override: user-entered macros always take precedence over either lookup source when
present on a `FoodItem`.

## Unit conversion domain model

Lives in `core/unit` as a pure-JVM, fully unit-tested `UnitConverter` (PR2) — no Android
framework dependency, so it's covered by fast JVM tests. `MeasurementUnit` is a bounded
enum (mass/volume/count categories); conversion factors are per-`FoodItem` (`UnitConversion`
rows), not global, since e.g. "1 cup" of rice vs. flour has different mass. This is called
out as accuracy-critical in the proposal — it must not be duplicated ad hoc in UI or
repository code; all conversion goes through this one use-case.

## Stock accounting (projected vs actual)

`ComputeProjectedStockUseCase` (PR4): `projected = currentStock - sum(planEntries where NOT
eaten)`. Actual stock is simply the `StockBatch` quantities as currently persisted — only
decremented by a dedicated mark-eaten use-case (PR8) when a `PlanEntry` is marked eaten.
Both are independently queryable; UI shows both views (PR6/PR9).

## Expiry reminders

`ExpiryCheckWorker` (WorkManager periodic, daily) scans `StockBatch.expiryDate`, surfaces
items expiring soon. Notification channel + `POST_NOTIFICATIONS` runtime request on API 33+.
Because permission can be denied, an in-app fallback surface (banner/badge on the pantry
screen driven by `ExpiryAlertRepository`) is mandatory, not just the system notification —
this was an explicit open risk in the proposal.

## Testing strategy (Strict TDD)

| Layer | Tool | Scope |
|---|---|---|
| Domain use-cases (`core/unit`, `*/domain`) | JUnit, pure JVM | Fast, no Android framework — RED/GREEN per use-case |
| Room DAOs | Robolectric + in-memory Room | DAO query correctness, relations |
| Network sources | MockWebServer | OFF/USDA fake responses, fallback/error paths |
| WorkManager | WorkManager `TestDriver` | `ExpiryCheckWorker` scheduling/execution |
| Compose UI | Compose UI test + instrumented | Screen-level behavior, requires emulator/device |

Robolectric+Room was chosen over instrumented-only DAO tests to keep the inner TDD loop
fast; a minimal instrumented smoke/migration test set is still required per PR to catch any
Robolectric/real-device fidelity gaps.

## Open questions carried into tasks

- Exact `MeasurementUnit` enum membership (finalize in PR2).
- Whether Robolectric+Room fidelity is sufficient long-term vs. fully instrumented DAO
  tests — revisit if gaps surface.
