# Spec: Macro Tracking

> Reconstructed 2026-07-12 after the Engram MCP backend disconnected mid-chain. See note in
> `specs/pantry-stock/spec.md`.

## ADDED Requirements

### Requirement: Item-to-Day Macro Rollup
Each `FoodItem` MUST store macro values (calories, protein, carbs, fat) per its canonical
unit, sourced from lookup (Open Food Facts / USDA FoodData Central) or manual entry. The
system MUST derive recipe-level and day-level macro totals by summing
`ingredient quantity (canonical unit) × item macros per canonical unit`; these totals MUST
be computed on demand, not stored as separately-editable truth.

#### Scenario: Recipe macro total derived from ingredients
- **Given** recipe "Chicken stir-fry" has 200g chicken (165 kcal/100g) and 185g rice (130
  kcal/100g)
- **When** the system computes the recipe's total calories
- **Then** it reports 330 + 240.5 = 570.5 kcal, derived from ingredient macros and
  quantities, not a separately stored recipe-level value

#### Scenario: Day total macro rollup across recipes and quick-add items
- **Given** Tuesday has one recipe `PlanEntry` and one quick-add `FoodItem` `PlanEntry`
- **When** the system computes Tuesday's macro totals
- **Then** it sums the derived recipe total and the quick-add item's macros
  (quantity × per-canonical-unit macros) into a single day total

#### Scenario: Missing macro data on an item
- **Given** a `FoodItem` was manually created without macro values (lookup skipped)
- **When** it contributes to a recipe or day macro rollup
- **Then** the system MUST distinguish "0 macros" from "unknown macros" in the rollup
  (e.g. flag the day/recipe total as incomplete rather than silently reporting a
  zero-inflated total)
