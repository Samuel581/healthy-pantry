# Spec: Meal Planning

> Reconstructed 2026-07-12 after the Engram MCP backend disconnected mid-chain. See note in
> `specs/pantry-stock/spec.md`.

## ADDED Requirements

### Requirement: Recipe CRUD
The system MUST allow the user to create, read, update, and delete `Recipe` records, each
with an ordered list of `RecipeIngredient` entries (a `FoodItem` reference, quantity, and
unit).

#### Scenario: Create a recipe with multiple ingredients
- **Given** the pantry contains "Chicken breast" and "Rice"
- **When** the user creates recipe "Chicken stir-fry" with 200g chicken and 1 cup rice
- **Then** the recipe is persisted with both `RecipeIngredient` rows referencing the correct
  `FoodItem`s

### Requirement: Weekly Plan Assignment
The system MUST allow the user to build a weekly plan by (a) assigning a `Recipe` to a
specific day, or (b) directly quick-adding a `FoodItem` + quantity to a specific day/meal
slot without a recipe. Both forms produce a `PlanEntry`.

#### Scenario: Assign a recipe to a day
- **Given** recipe "Chicken stir-fry" exists
- **When** the user assigns it to Tuesday
- **Then** a `PlanEntry` is created for Tuesday referencing the recipe, and its ingredient
  quantities are counted toward that day's/week's ingredient needs

#### Scenario: Quick-add a raw item without a recipe
- **Given** the pantry contains "Bananas"
- **When** the user quick-adds 2 bananas to Wednesday breakfast without creating a recipe
- **Then** a `PlanEntry` is created for Wednesday referencing the `FoodItem` directly with
  quantity 2, no recipe involved

### Requirement: Weekly Ingredient Needs
The system MUST compute the total quantity of each `FoodItem` required across all
not-yet-eaten `PlanEntry` rows in the current week, converting each ingredient's expressed
unit to the item's canonical unit via its registered `UnitConversion` factors.

#### Scenario: Sum ingredient needs across multiple recipes in the week
- **Given** two recipes assigned this week each requiring rice (1 cup and 0.5 cup
  respectively), and "Rice" has conversion 1 cup = 185g
- **When** the user views this week's ingredient needs
- **Then** the system reports 277.5g of rice needed for the week, summed and converted to
  the canonical unit

### Requirement: Mark Plan Entry Eaten
The system MUST allow the user to mark a `PlanEntry` as eaten, which excludes it from
future projected-stock and weekly-needs calculations and triggers actual stock deduction
(see `specs/pantry-stock/spec.md`, Requirement: Projected vs Actual Stock).

#### Scenario: Marking eaten removes the entry from projected deficit
- **Given** a `PlanEntry` for Friday is not yet eaten and is included in this week's
  projected deficit
- **When** the user marks it eaten
- **Then** it is excluded from projected-stock and weekly-needs calculations going forward,
  and actual stock is decremented accordingly
