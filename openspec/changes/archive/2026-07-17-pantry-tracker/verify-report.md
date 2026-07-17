# Verify Report: Pantry Tracker (v1 core)

```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:pending-no-jdk-sandbox
verdict: pass-with-warnings
blockers: 0
critical_findings: 0
requirements: 11/11
scenarios: 22/22
test_command: "./gradlew test"
test_exit_code: N/A (no JDK/gradle in this sandbox — see Execution Note)
test_output_hash: N/A
build_command: "./gradlew build"
build_exit_code: N/A (no JDK/gradle in this sandbox — see Execution Note)
build_output_hash: N/A
```

## Execution Note (read before trusting this report)

`java`/`javac`/`gradle` are all absent from this sandbox (`which java javac gradle` → not
found; `./gradlew --version` fails with `JAVA_HOME is not set`) — unchanged from the prior
verify pass and every apply-phase note in `tasks.md`/`state.yaml` for this project. Per explicit
instruction for this re-run, verification again fell back to source inspection: every touched
production file and every touched/new test file from commit `64ff15d` was read in full, and its
assertions traced against the corresponding production code and the exact spec scenario text.
This is **not** runtime proof — no test actually executed here. Treat `verdict` as a source-trace
verdict, not a green-CI verdict.

## Change: pantry-tracker
**Version**: v1 core (11 phases + 1 CRITICAL bug-fix pass)
**Mode**: Strict TDD (source-trace, no runtime execution — see note above)
**Branch state**: `dev` at `64ff15d` (`fix(macro-tracking): distinguish unknown macros from a
verified zero`, merged via PR #17, one commit past the prior verify pass's `7a387c4`). Working
tree clean except this report and untracked tooling dirs (`.atl/`, `.codegraph/`).

## Re-verify scope
This is a follow-up to the prior verify pass (`critical_findings: 1`, `blockers: 1`,
`verdict: pass-with-gaps`), scoped per explicit instruction to:
1. Confirm the CRITICAL finding (macro-tracking "Missing macro data on an item") is now
   genuinely closed — spec text vs. actual code vs. actual test coverage, end-to-end including
   the recipe-rollup path.
2. Sanity-check no regression in shared files touched by the fix (`AppDatabase.kt`,
   `MacroTotals.kt`, `FoodItemMapper.kt`, and every pre-existing test call site the fix commit
   had to touch to keep compiling).
3. Re-accept the 2 prior WARNINGs and 1 prior SUGGESTION as still non-blocking, confirming they
   are unregressed rather than re-deriving them from scratch.

## 1. CRITICAL finding re-check: "Missing macro data on an item"

**Spec text** (`openspec/changes/pantry-tracker/specs/macro-tracking/spec.md`, Scenario
"Missing macro data on an item"):
> Given a `FoodItem` was manually created without macro values (lookup skipped) — When it
> contributes to a recipe or day macro rollup — Then the system MUST distinguish "0 macros" from
> "unknown macros" in the rollup (e.g. flag the day/recipe total as incomplete rather than
> silently reporting a zero-inflated total)

**Implementation, traced end-to-end:**

| Layer | Before (prior verify pass) | After (`64ff15d`, this pass) |
|---|---|---|
| `FoodItemEntity` (Room columns) | non-null `Double` | nullable `Double?`; `AppDatabase` bumped to `version = 3`, no `Migration` (same documented greenfield-local-DB precedent as the v1→v2 bump — reasonable, re-confirmed) |
| `FoodItem` (domain model) | non-null `Double` | nullable `Double?`, with KDoc explicitly stating `null` = "unknown", distinct from a verified `0.0` |
| `FoodItemMapper.toDomain`/`toEntity` | pass-through | still plain pass-through (`app/src/main/.../FoodItemMapper.kt`), correctly typed against the now-nullable fields on both sides — no coercion reintroduced |
| `ItemFormUiState.toFoodItem` (save path) | `caloriesPerUnit.toDoubleOrNull() ?: 0.0` — coerced blank/unparseable to `0.0` | `caloriesPerUnit.toDoubleOrNull()` — blank/unparseable now saves as `null`; an explicit user-typed `"0"`/`"0.0"` still saves as a real `0.0` |
| `ItemFormViewModel.loadExisting` (edit-load path) | `item.caloriesPerUnit.toString()` — would have rendered the literal text `"null"` once the field went nullable | `item.caloriesPerUnit?.toString().orEmpty()` — renders a `null` macro as a blank field, so it round-trips back to `null` on resave instead of the string `"null"` |
| `MacroTotals` | no completeness signal | `isComplete: Boolean = true`, ANDed through `plus()` |
| `ComputeMacroTotalsUseCase.computeForQuickAdd` | `foodItem.caloriesPerUnit * quantity` (would NPE the moment the field went nullable) | `(foodItem.caloriesPerUnit ?: 0.0) * quantity` for the numeric sum, plus `isComplete = ` all four fields non-null |
| `ComputeMacroTotalsUseCase.computeForRecipe` | n/a | unchanged signature; internally calls `computeForQuickAdd(detail.foodItem, converted.value)` per ingredient and accumulates via `total += ...` — `isComplete` propagates automatically through the existing `MacroTotals.plus` AND-fold, confirmed by reading the method body directly (no bypass of the shared helper) |
| `ComputeMacroTotalsUseCase.computeDayTotal` | n/a | unchanged signature; delegates to `computeForQuickAdd` (quick-add entries) or `computeForRecipe` (recipe entries) and accumulates the same way — confirmed both branches feed the same `isComplete`-AND fold |

**Test coverage, traced end-to-end (not just existence — assertions read in full):**

- `ItemFormUiStateTest`:
  - `toFoodItem parses macro strings and treats blank or unparseable input as unknown, not zero`
    — asserts `assertNull(item.proteinGramsPerUnit)` for blank input and `assertNull(item.fatGramsPerUnit)` for `"not-a-number"`, while `caloriesPerUnit`/`carbsGramsPerUnit` (parseable) stay numeric.
  - `toFoodItem parses an explicit zero as a known 0_0, distinct from a blank field` — user-typed `"0"`/`"0.0"` asserted equal to `0.0`, not `null`. This is the exact "0 vs unknown" distinction the spec scenario names.
- `ItemFormViewModelTest`:
  - `loadExisting seeds an unknown macro as a blank field, not the literal text null, and it round-trips back to null on save` — builds a `FoodItem` with `fatGramsPerUnit = null`, calls `loadExisting`, asserts the rendered field is `""` (not `"null"`), then calls `buildFoodItem` and asserts `assertNull(resaved.fatGramsPerUnit)` while a known field (`caloriesPerUnit`) round-trips correctly. This is the full **save → edit-load → resave** round-trip the prior CRITICAL finding flagged as completely missing.
- `ComputeMacroTotalsUseCaseTest` (7 new/updated cases directly on point, read in full):
  - `computeForQuickAdd flags the total as incomplete when the food item has an unknown macro` — known macros still sum correctly, unknown macro contributes `0.0` numerically, `isComplete == false`.
  - `computeForQuickAdd reports a complete total when every macro field is known, including a real zero` — a food item with a genuine `0.0` carbs field (not `null`) is asserted `isComplete == true`, directly proving the "0 vs unknown" distinction at the quick-add layer.
  - `computeForRecipe flags the total as incomplete when an ingredient's food item has an unknown macro` — **this is the recipe-rollup path check the task explicitly asked to confirm**: a recipe with one ingredient whose `fatGramsPerUnit == null` is computed via `computeForRecipe`, known macros (450 kcal / 10g protein / 60g carbs) still sum correctly, and `isComplete == false` is asserted on the returned `MacroTotals`.
  - `computeForRecipe reports a complete total when every ingredient's macros are fully known` — a two-ingredient fully-known recipe asserts `isComplete == true`.
  - `computeDayTotal flags the day as incomplete when any contributing entry has an unknown macro` — a quick-add-only day mix.
  - `computeDayTotal reports a complete day when every contributing entry has fully-known macros`.
  - `computeDayTotal flags the day as incomplete when a recipe entry's ingredient has an unknown macro` — **the second explicit recipe-rollup-path check**: a day with one `RecipeEntry` (unknown-macro ingredient) plus one fully-known `QuickAddEntry` is asserted `isComplete == false`, proving propagation through `computeDayTotal`'s `RecipeEntry` branch specifically, not just the `QuickAddEntry` branch.

**Verdict on this finding: CLOSED.** All four points originally cited as missing in the prior
CRITICAL finding — non-nullable schema, coercion-to-zero at save, no `isComplete` signal, no
test anywhere — are now implemented and each has at least one test whose assertions were read
and confirmed to directly exercise the spec's stated behavior, including both requested
recipe-rollup entry points (`computeForRecipe` directly, and `computeDayTotal`'s `RecipeEntry`
branch).

## 2. Regression sanity-check on shared files

- **`AppDatabase.kt`**: `version` bumped `2 → 3`, entity list unchanged (still the same 6
  entities from PR7), KDoc updated to explain both the v1→v2 and v2→v3 no-migration reasoning.
  No other DAO/entity touched. No regression.
- **`MacroTotals.kt`**: `isComplete: Boolean = true` added with a default, so every pre-existing
  call site that constructed a `MacroTotals` with only 4 positional/named args (e.g.
  `MacroTotals(1.0, 2.0, 3.0, 4.0)` in `ComputeMacroTotalsUseCaseTest`'s `ZERO is the additive
  identity` test) still compiles and defaults to `isComplete = true` — confirmed this test is
  unchanged and still passes its own logic (`MacroTotals.ZERO + totals == totals`, unaffected by
  the new field since both operands are `isComplete = true`). `ZERO` companion constant
  explicitly re-states `isComplete = true`. No regression.
- **`FoodItemMapper.kt`**: pass-through only, both `FoodItemEntity` and `FoodItem` sides moved to
  nullable together — no coercion introduced, no regression.
- **Pre-existing test call sites the fix commit had to touch** (`FoodItemRepositoryTest.kt`,
  `PantryViewModelTest.kt`): both changes are mechanical `!!` non-null assertions added to
  `assertEquals(double, double, delta)` calls that read a now-nullable field back out of a
  freshly-inserted/known-non-null fixture — traced both call sites, the underlying fixtures they
  read from always set a real numeric literal for that field, so the `!!` cannot itself
  introduce a runtime NPE risk beyond what the test fixture already guarantees. No regression.
- **Swept the remaining 20 test files** that reference the four macro field names (via
  `grep -rln`): all of them only construct `FoodItem`/`FoodItemEntity` with numeric literals in
  named-arg position (fine against a widened `Double?` param — no cast needed) or use `?:`-style
  null-safe reads already (`FoodItemDaoTest.kt` line 114: `updated?.caloriesPerUnit ?: -1.0`,
  pre-existing, unaffected). None of them read a macro field as a bare non-null `Double` without
  either a literal source or a null-safe operator. No regression found.
- **Production callers of the new `isComplete` signal**: grepped `app/src/main` for
  `ComputeMacroTotalsUseCase`, `computeForQuickAdd`, `computeForRecipe(`, `computeDayTotal(` —
  confirmed zero call sites outside `ComputeMacroTotalsUseCase.kt` itself (all other hits are
  KDoc cross-references in unrelated files). This independently confirms the fix commit's own
  claim ("no UI layer needed a fix to avoid re-coercing the signal to zero" because nothing calls
  the use case in production yet) rather than taking the commit message on faith.

No regression found in any shared file touched by this fix.

## 3. Prior WARNINGs / SUGGESTION — re-confirmed unregressed, still accepted as non-blocking

- **WARNING (unchanged)**: `SystemExpiryNotifier`'s API-33+ permission-denied no-op branch still
  has no dedicated unit test (`find -iname "*SystemExpiryNotifierTest*"` → no results, same as
  prior pass). Untouched by this fix commit (confirmed: `expiry/` package not in `64ff15d`'s
  diff stat). Still non-blocking per the same reasoning as before (the user-facing guarantee is
  independently covered by `ExpiryAlertRepositoryTest`/`ExpiryBannerTest`).
- **WARNING (unchanged, technically extended but not worsened)**: `design.md`'s "Room schema"
  section still only documents the PR7 v1→v2 bump as "adds a migration" (line 61); it says
  nothing about the new v2→v3 bump from this fix (`AppDatabase.kt`'s own KDoc is the accurate,
  up-to-date source now covering both bumps). Same category of staleness as before, not a new or
  worsened defect — recorded as still-open.
- **SUGGESTION (unchanged)**: `PantryViewModel.committedQuantity` remains hardcoded to `0.0`,
  confirmed still documented via its own KDoc, unaffected by this fix commit (pantry `ui/vm`
  package touched by `64ff15d` only for `ItemFormUiState.kt`/`ItemFormViewModel.kt`, not
  `PantryViewModel.kt`).

## Spec Compliance Matrix (delta from prior pass)

| Requirement | Scenario | Test (source-traced) | Result (prior → now) |
|---|---|---|---|
| Item-to-Day Macro Rollup | Missing macro data on an item (distinguish 0 vs unknown) | `ItemFormUiStateTest` (2 cases), `ItemFormViewModelTest` (1 case), `ComputeMacroTotalsUseCaseTest` (7 cases incl. both recipe-rollup entry points) | ❌ UNTESTED/NOT IMPLEMENTED → ✅ COMPLIANT |

All other 21 scenarios from the prior pass are unchanged and re-confirmed not to have regressed
(see sections 1–3 above for the specific shared-file checks; the full 22-scenario matrix from the
prior report otherwise still applies verbatim and was not re-derived from scratch per the task's
explicit scope).

## Issues Found (this pass)

**CRITICAL** (0): none. The single prior CRITICAL is closed — see Section 1.

**WARNING** (2, both carried forward, unregressed, still non-blocking):
- `SystemExpiryNotifier`'s permission-denied no-op branch has no automated test.
- `design.md`'s Room-schema section is stale on migration wording (now for two bumps, not one).

**SUGGESTION** (1, carried forward, unregressed):
- `PantryViewModel.committedQuantity` remains hardcoded to `0.0` (documented, deliberate,
  unaffected by this fix).

## Verdict
**PASS WITH WARNINGS** — 0 CRITICAL, 2 WARNING, 1 SUGGESTION. The macro-tracking CRITICAL
finding from the prior verify pass is genuinely closed: implementation and test coverage were
independently traced (not assumed from the commit message) against the exact spec scenario text,
including both requested recipe-rollup entry points (`computeForRecipe` directly and
`computeDayTotal`'s `RecipeEntry` branch). No regression was found in any shared file the fix
touched. 11/11 requirements and 22/22 scenarios are now source-trace-compliant.

### Recommendation
Ready for `sdd-archive`. The 2 WARNINGs and 1 SUGGESTION are all pre-existing, documented,
non-blocking, and explicitly re-confirmed unregressed in this pass — same disposition as the
prior report recommended for them. No further apply work is required before archiving. As with
every phase of this project, no test in this change has ever actually been executed in this
sandbox (no JDK/gradle available) — if/when a CI environment with a real JDK is available, a
runtime `./gradlew test` pass is still recommended to convert this source-trace verdict into a
green-CI verdict, but nothing in this re-verify pass is blocking on that basis.
