# Proposal: Pantry Tracker (v1 core)

> Reconstructed 2026-07-12 after the Engram MCP backend disconnected mid-chain. This
> file reproduces the approved proposal content from conversation record; the original
> Engram-stored proposal (topic_key `sdd/pantry-tracker/proposal`) is no longer reachable.

## Intent

An Android app that is a single, tightly-coupled loop across three feature areas, all in
scope for v1 (the user explicitly rejected a narrower MVP — all three are wanted from day
one):

1. **Pantry stock tracking** — quantities of food on hand.
2. **Weekly meal planning** — plan what to eat this week, see how much of each ingredient
   the plan will consume.
3. **Macro tracking** — nutrition (calories/protein/carbs/fat) per food item, rolled up per
   recipe/meal/day where possible.

## Scope (confirmed with user during brainstorming)

- **Item entry**: Scan barcode (Google Code Scanner — `GmsBarcodeScannerOptions`, no camera
  permission needed) → look up via Open Food Facts API for name+macros. Fallback: manual
  entry, including typing a food name and searching USDA FoodData Central for macros (for
  raw/unpackaged ingredients OFF doesn't cover well, e.g. fresh produce). Both lookup
  sources sit behind one `NutritionLookupRepository` interface.
- **Persistence**: Local only, Room database. No cloud, no Firebase, no accounts/auth, no
  cross-device sync. (The repo's `.gitignore` has Firebase-flavored entries — confirmed
  template boilerplate, not an intentional dependency.)
- **User/pantry scope**: Single user, single pantry. No multi-pantry, no household sharing.
- **Meal planning unit**: BOTH — (a) reusable recipes (name + ordered ingredient list, each
  ingredient = pantry-item reference + quantity + unit), assignable to days of the week; and
  (b) direct quick-add of a raw pantry item + quantity to a specific day/meal slot without
  going through a recipe.
- **Stock accounting**: Dual view. "Projected remaining stock" = current stock minus
  everything currently planned but not yet marked eaten. "Actual stock" = current stock,
  only decremented when the user explicitly marks a planned meal/item as eaten. Both views
  must be independently queryable/displayable.
- **Units**: Each pantry item can define conversion factors between its units (e.g. 1 cup
  rice = 185g), so recipes can express ingredient quantities in natural units while stock is
  tracked in one canonical unit per item.
- **Expiry tracking**: Yes, with reminders. Each stock entry/batch carries an expiry date. A
  daily background check (WorkManager periodic work) surfaces "expiring soon" items and
  posts a local notification (requires `POST_NOTIFICATIONS` runtime permission on API 33+).
- **Macro rollup**: Macros are per-food-item (from lookup or manual entry). Recipe-level and
  day-level macros are computed by summing ingredient macros × quantity — derived, not
  separately stored truth.

## Approved technical approach

- **UI**: Kotlin + Jetpack Compose, Material 3 components (Scaffold + bottom NavigationBar).
- **Architecture**: UI layer (Compose + ViewModel, `StateFlow` via
  `collectAsStateWithLifecycle`) → optional Domain layer (use-cases, e.g. weekly ingredient
  deficit, projected vs actual stock) → Data layer (repositories wrapping Room DAOs + the
  nutrition-lookup network layer).
- **DI**: Hilt (`hiltViewModel()` in Compose).
- **Persistence**: Room, Flow-based DAO queries. Entities/relations: pantry items (unit +
  conversion factors), stock batches (quantity + expiry, per item), recipes, recipe
  ingredients (item + quantity + unit), weekly plan entries (day + recipe-or-item +
  quantity), consumption/eaten-state.
- **Background work**: WorkManager daily periodic worker → expiry-check → local
  notification.
- **Min SDK**: 26. Target/compile SDK: 36 (Android 16), confirmed current stable as of July
  2026.
- Strict TDD mode is active for this project (per `sdd-init`) — carried forward to
  spec/design/tasks as a constraint.

## Non-goals

- No cloud sync, accounts, or multi-device support.
- No multi-pantry or household/shared-pantry support.
- No custom in-app camera scanning UI (Google Code Scanner only, not CameraX+ML Kit raw
  integration).
- No recipe sharing/import from external sources beyond the two nutrition APIs named.

## Risks

- Open Food Facts data quality/coverage gaps — mitigated by USDA fallback plus manual macro
  override.
- USDA FoodData Central requires a self-serve API key — provisioned via `local.properties` +
  `BuildConfig` field, documented in README, defaults to `DEMO_KEY` for a working clean
  checkout (low rate limit on the demo key — must be swapped for real use).
- Unit-conversion correctness is accuracy-relevant (drives what the user believes is in
  stock) — must live in a tested domain use-case.
- `POST_NOTIFICATIONS` (API 33+) can be denied, silencing expiry reminders — needs an
  in-app fallback surface (banner/badge), not notification-only.

## Delivery convention

One branch + one PR per task/work-unit. Every branch is cut from `dev`; every PR targets
`dev` directly. Serial delivery: a task's branch is cut only after the previous task's PR
has merged into `dev` (not a stacked chain) — chosen explicitly over `feature-branch-chain`
to keep every PR targeting `dev` as requested.
