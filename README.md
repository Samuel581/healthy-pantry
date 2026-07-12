# Healthy Pantry

Android app (Kotlin + Jetpack Compose) for tracking pantry stock, planning meals, and
staying on top of macros — see `sdd/pantry-tracker/*` artifacts for full spec/design/tasks.

## Setup

### Requirements
- JDK 17 (Gradle's toolchain auto-provisioning will fetch one if not found locally)
- Android SDK: compileSdk/targetSdk 36, minSdk 26

### USDA FoodData Central API key

Nutrition lookup falls back to the [USDA FoodData Central API](https://fdc.nal.usda.gov/api-guide)
for raw produce macros not covered by Open Food Facts. Get a free API key at
https://fdc.nal.usda.gov/api-key-signup and add it to `local.properties` (already gitignored,
never commit it):

```properties
USDA_FDC_API_KEY=your-key-here
```

If unset, the app builds with a placeholder `DEMO_KEY` value (see `app/build.gradle.kts`) so
a clean checkout always builds; USDA search will show a configuration error until a real key
is provided (per spec: "Missing USDA API key" scenario), but manual macro entry always works.

Open Food Facts (barcode lookup) requires no key.

## Build & test

```bash
./gradlew build
./gradlew test                          # JVM unit tests
./gradlew connectedDebugAndroidTest      # instrumented tests (requires a device/emulator)
```
