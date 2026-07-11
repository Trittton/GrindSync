# GrindSync — offline-first workout tracker

Clean-room Android fitness app: RepCount-equivalent logging + nutrition +
Solo-Leveling-style ranking. See [SPEC.md](SPEC.md) for the full spec and
[nav-schematic.html](nav-schematic.html) for the wireframe.

## Status: Phase 0 (skeleton + core schema)

- Single `:app` module; SPEC §4.2 boundaries kept as packages
  (`core/model`, `core/database`, `core/datastore`, `core/ui`, `core/di`,
  `feature/workout`, `feature/nutrition`, `feature/profile`, `app`).
  Split into real Gradle modules when the build or team grows.
- Stack: Kotlin 2.2, Jetpack Compose (BOM 2025.06), Hilt, Room 2.7,
  Navigation-Compose, DataStore. `compileSdk 36`, `minSdk 29`.

## Non-negotiables encoded here

| Rule | Where |
|---|---|
| Weights stored canonical **kg**; kg/lb is display-only | `WorkoutEntity` / `SetEntryEntity` docs, `UserPreferencesRepository` |
| Polymorphic `SetEntry` gated by `ExerciseType` | `core/model/ExerciseType.kt` (`SetValidation`), nullable columns in `SetEntryEntity` |
| `ExerciseMuscle` mapping from day one | `ExerciseMuscleEntity` + seeded weights (primary 1.0 / secondary 0.4) |
| Derived data recomputable | gamification tables are Phase 3 *caches*, never source of truth |
| Tested migrations, no destructive fallback | `Migrations.kt`, `MigrationTest.kt`, schemas exported to `app/schemas/` |

## Build & run

```
gradlew :app:assembleDebug          # build APK
gradlew :app:testDebugUnitTest      # JVM tests (set validation)
gradlew :app:connectedDebugAndroidTest  # device tests (seed + migration)
```

Gradle needs JDK 17–21 (Android Studio's bundled JBR works). The Room schema
history in `app/schemas/` is committed on purpose — migration tests build old
versions from it.
