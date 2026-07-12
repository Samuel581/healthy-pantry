# Spec: Nutrition Lookup

> Reconstructed 2026-07-12 after the Engram MCP backend disconnected mid-chain. See note in
> `specs/pantry-stock/spec.md`.

## ADDED Requirements

### Requirement: Barcode Scan, Manual Entry with USDA Fallback
The system MUST support adding a `FoodItem` via barcode scan (Google Code Scanner, no
camera permission required) followed by an Open Food Facts lookup for name and macros. The
system MUST support manual entry as a fallback, including a name-search against USDA
FoodData Central for macros when Open Food Facts has no match (e.g. fresh produce, bulk
items without a barcode). Both sources MUST be accessed through a single
`NutritionLookupRepository` interface so callers do not depend on which source answered.

#### Scenario: Successful barcode scan and OFF lookup
- **Given** the user scans a barcode present in Open Food Facts
- **When** the lookup completes
- **Then** the `FoodItem` form is pre-filled with name and macros from Open Food Facts, and
  the user MAY edit before saving

#### Scenario: Barcode not found in Open Food Facts
- **Given** the user scans a barcode with no Open Food Facts match
- **When** the lookup returns no result
- **Then** the system falls back to manual entry with a name-search against USDA FoodData
  Central pre-populated from any partial data available (e.g. barcode digits), rather than
  failing silently

#### Scenario: Manual name search against USDA FoodData Central
- **Given** the user is manually adding "raw broccoli" with no barcode
- **When** they search by name
- **Then** the system queries USDA FoodData Central and lets the user select a matching
  entry to pre-fill macros

#### Scenario: Manual macro override always wins
- **Given** a `FoodItem` was created from an OFF or USDA lookup
- **When** the user edits the macro values directly
- **Then** the manually entered values are persisted and MUST take precedence over any
  future re-lookup of the same source

#### Scenario: USDA API key not configured
- **Given** the app is built without a real `USDA_FDC_API_KEY` (using the `DEMO_KEY`
  default)
- **When** the user performs many USDA searches in a short period
- **Then** the system MUST surface a rate-limit error distinctly from a "no results found"
  error, so the user understands to configure a real key
