# Healthy Pantry

Android app (Kotlin + Jetpack Compose) for tracking pantry stock, planning meals, and
staying on top of macros — see `openspec/changes/pantry-tracker/*` for full spec/design/tasks.

## Overview

Single-module Android app (Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager).
Three bottom-nav tabs host the app's features:

- **Pantry** — food items and stock batches (barcode scan or manual entry, with USDA/Open Food
  Facts nutrition lookup), showing both actual and projected stock.
- **Recipes** — recipes and their ingredients.
- **Plan** — this week's meal plan (assign a recipe or quick-add a raw item per day/meal slot,
  mark entries eaten), plus a weekly macro rollup.

A daily background check (WorkManager) surfaces pantry items nearing or past their expiry date
via a local notification and an in-app banner.

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

## Run

With a device connected or an emulator running:

```bash
./gradlew installDebug
```

or open the project in Android Studio and run the `app` configuration. The app launches
directly on the Pantry tab; use the bottom navigation bar to switch to Recipes or Plan.
