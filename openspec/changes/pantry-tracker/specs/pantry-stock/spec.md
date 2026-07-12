# Spec: Pantry Stock

> Reconstructed 2026-07-12 after the Engram MCP backend disconnected mid-chain, from the
> `sdd-spec` phase's executive summary and the proposal's scope decisions. Requirement
> wording is reconstructed, not verbatim from the original Engram-stored spec.

## ADDED Requirements

### Requirement: Item and Stock Batch CRUD
The system MUST allow the user to create, read, update, and delete `FoodItem` records and
`StockBatch` records (a quantity of a given item, added at a point in time, with an
optional expiry date).

#### Scenario: Add a new stock batch for an existing item
- **Given** a `FoodItem` "Chicken breast" already exists with canonical unit grams
- **When** the user adds a stock batch of 500g with expiry date 2026-07-20
- **Then** a new `StockBatch` row is persisted linked to that `FoodItem`, and the item's
  total on-hand quantity reflects the addition

#### Scenario: Delete a stock batch
- **Given** a `StockBatch` exists with remaining quantity > 0
- **When** the user deletes it
- **Then** it MUST NOT be counted in any future stock calculation (projected or actual)

### Requirement: Unit Conversion Correctness
Each `FoodItem` MAY define one or more `UnitConversion` factors (e.g. 1 cup rice = 185g).
The system MUST use these factors, and only these factors, to convert between a recipe's
expressed unit and the item's canonical stock unit. The system MUST NOT apply a global or
hardcoded conversion table across items.

#### Scenario: Convert a recipe quantity to canonical unit for stock deduction
- **Given** "Rice" has canonical unit grams and a conversion 1 cup = 185g
- **When** a recipe ingredient specifies 2 cups of rice
- **Then** the system computes 370g against the "Rice" stock, not against any other item's
  conversion factor

#### Scenario: No conversion factor registered for the requested unit
- **Given** "Olive oil" has canonical unit ml with no registered conversion to "tablespoon"
- **When** a recipe ingredient specifies quantity in tablespoons
- **Then** the system MUST surface this as an unresolved-unit error to the user rather than
  guessing or silently defaulting to a 1:1 conversion

### Requirement: Projected vs Actual Stock
The system MUST expose two independently queryable stock views per `FoodItem`:
- **Actual stock**: the sum of current `StockBatch` quantities, only reduced when a planned
  item/meal is explicitly marked eaten.
- **Projected stock**: actual stock minus the sum of quantities committed to `PlanEntry`
  rows that are not yet marked eaten.

#### Scenario: Projected stock reflects an unconsumed plan
- **Given** "Chicken breast" has 500g actual stock and a `PlanEntry` for Tuesday consuming
  200g that has not been marked eaten
- **When** the user views projected stock
- **Then** projected stock for "Chicken breast" is 300g, while actual stock remains 500g

#### Scenario: Marking a planned meal eaten updates actual stock
- **Given** the Tuesday `PlanEntry` above is marked eaten
- **When** the mark-eaten action completes
- **Then** actual stock for "Chicken breast" decreases by 200g to 300g, and projected stock
  recalculates to match actual stock (no longer subtracting the now-eaten entry)
