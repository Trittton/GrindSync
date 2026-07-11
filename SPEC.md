# Fitness App — Specification & Architecture Document

> **Status:** Draft v0.1 — research/planning stage
> **Author roles contributing:** Requirements Specialist, Software Architect, UI/UX Designer, QA, (Game Designer for the gamification module)
> **Target:** Native Android, personal use (owner + a small circle of friends)
> **Basis:** Clean-room feature study of RepCount (`sp.repcount`) performed 2026-07-11 on a live Pixel 9, plus verified public sources.

---

## 0. Legal & scope framing (read first)

**Requirements Specialist:** This project is a **clean-room reimplementation** — we studied *what* RepCount does (features, information architecture, UX) and we build our own from scratch. We do **not** copy its code, decompiled artifacts, assets, icons, or the "RepCount" name/branding. That is legal and standard practice for building in a category.

We are explicitly **not** cracking, patching, or redistributing RepCount's premium tier. We don't need to: every RepCount *premium* feature (charts, 1RM estimates, PR tracking, CSV export) is a plain calculation over data the user logs themselves, so we get equivalent functionality for free simply by building it.

**Do / Don't:**
- ✅ Reproduce common, non-proprietary concepts: set/rep logging, rest timers, routine templates, volume charts.
- ✅ Ship a superset of features (nutrition, gamification) that RepCount doesn't have.
- ❌ Reuse RepCount's exact copy, icon set, or any extracted resource.
- ❌ Distribute a modified RepCount APK.

---

## 1. Product overview

A private, **offline-first Android** workout tracker that matches RepCount's core logging + stats, then extends it with:

1. **Nutrition tracking** — calories + macros (protein / carbs / fat).
2. **Gamified profile** — "Solo Leveling"-style per-exercise levels, XP, and a normalized strength stat sheet.
3. **Statistics** — the analytics RepCount paywalls, available by default.

**Primary users:** the owner and a few friends. **Scale:** single-digit users. **Monetization:** none. This drives every architecture decision toward *simplicity and local ownership of data*, not scalability.

---

## 2. RepCount analysis (observed, live app)

### 2.1 Information architecture
Bottom navigation with three tabs plus a left drawer:

| Surface | Purpose |
|---|---|
| **Log** (tab) | Reverse-chronological history of completed workouts, grouped by date. FAB to add a workout. |
| **Routines** (tab) | List of reusable workout templates (e.g. a full mesocycle `w1d1…w3d2`). FAB to add. |
| **Statistics** (tab) | Analytics dashboard — **entirely paywalled** in free. |
| **Drawer** | Edit Exercises, Edit Categories, Account, Settings, Feedback, Help, light/dark toggle. |

### 2.2 Workout (Log entry) structure
Observed on a real workout:
- Header: **Name**, **bodyweight value + unit** (`BW (Kg)`), **Date**, **Start Time**, **End Time**, **Notes**.
- Ordered list of **exercises**; each exercise contains an ordered list of **sets**.
- Per set: a **warmup/working marker** (empty circle = warmup, numbered = working set), **Kg**, **Reps**, per-set **Notes**, and a per-set overflow menu.
- Per exercise: **Add Set**, quick **history**, quick **chart**, **favorite/PR** icons, and an overflow menu (superset/dropset live here — premium).
- Per workout: a **rest timer** in the header.

### 2.3 Routine (template) structure
- **Name**, a **Targets** mode (observed value `Latest` = prefill weights/reps from last performance), **Notes**.
- Ordered exercises, each defined by **target set count + rep range** (e.g. "3 Sets, 6–12 reps") — *not* fixed weights.
- A **START** action instantiates a live workout from the template.

### 2.4 Exercise catalog
- Large pre-seeded, alphabetized library + user-added customs.
- Exercise editor fields: **Name**, **Category** (muscle group, e.g. Biceps), **Exercise Type** (tracking mode), **Single Leg / Single Arm** flag ("count weight twice in statistics"), **Transfer Exercise Data** (merge history into another exercise), **Delete**.

### 2.5 Exercise Type taxonomy (critical for the data model)
The tracking mode determines which measurement fields a set stores. Observed options:

| Exercise Type | Fields captured | Examples |
|---|---|---|
| Strength: Weight, Reps | weight, reps | Bench Press, DB Row |
| Strength: Weight, Time | weight, time | Weighted static holds |
| Strength: Weight, Distance | weight, distance | Farmer's walk, sled push |
| Strength: Weight, Distance, Time | weight, distance, time | Weighted carry for time |
| Bodyweight: Weight, Reps | added_weight, reps | Dips, Pull-up, Chin-up |
| Bodyweight: Reps | reps | Push-ups, BW squat |
| Bodyweight: Time | time | Plank, wall sit |
| Assisted bodyweight: Weight, Reps | assist_weight, reps | Assisted dips/chin-up |
| Cardio: Time, Distance, Kcal | time, distance, kcal | Running, stationary bike |
| Other: Notes | notes only | — |

**Implication:** the set record must be **polymorphic** — a superset of nullable measurement fields gated by the parent exercise's type. See §5.

### 2.6 Free vs Premium boundary (verified in-app + public sources)
- **Free:** unlimited workout logging, custom routines & exercises, rest timer, history with "prefill from last session," cardio logging. No ads.
- **Premium (~$29.99/yr; observed 199 kr/yr w/ 7-day trial):** **Advanced Stats & Charts**, **Personal Record Tracking**, **Supersets & dropsets**, **Quick exercise history**, **CSV Export**. The entire Statistics tab (Number of Workouts, Duration, Volume, Total Sets/Reps, Reps per Set, …) is locked.

**QA note:** RepCount paywalls **data export (CSV)**. For our clean-room app, unrestricted export/import is a first-class requirement (§10) — it's the user's own data and the mechanism friends will use to share.

**Sources:** repcountapp.com, Google Play listing (`sp.repcount`), support.repcountapp.com. Cross-checked against the live app on-device.

---

## 3. Scope for our app

### 3.1 In scope (v1)
- **P0 — Workout core:** exercise catalog (seeded + custom), routines/templates with rep-range targets & prefill, live workout logging with the polymorphic set model, warmup markers, per-set notes, rest timer, workout history.
- **P0 — Statistics (unpaywalled):** volume over time, estimated 1RM, PRs (all rep ranges), per-exercise progress charts, totals.
- **P1 — Nutrition:** manual food entry, calories + macros, daily targets, food favorites, Open Food Facts search.
- **P1 — Gamification profile:** per-exercise XP/levels, normalized strength stat sheet, streaks, achievements.
- **P2 — Data portability:** JSON/CSV export & import (also the "share with friends" mechanism).

### 3.2 Out of scope (v1)
- Cloud sync / accounts / social leaderboards (revisit in Phase 5 only if wanted).
- Barcode scanning for nutrition (Phase 4+).
- iOS.
- Wear OS / health-platform integration.

### 3.3 Explicitly deferred decisions
See §12 (open questions) — do not build past P0 until these are answered.

---

## 4. Technical architecture

**Software Architect:** Optimize for a solo/small-group offline app. No backend until proven necessary.

### 4.1 Stack
| Concern | Choice | Rationale |
|---|---|---|
| Language | **Kotlin** | Android standard. |
| UI | **Jetpack Compose** | Declarative UI; faster to build than XML layouts. |
| Architecture | **MVVM + Repository** | Clean separation UI ↔ logic ↔ data; scales to nutrition + gamification without entanglement. |
| Local DB | **Room** (SQLite) | Type-safe SQLite wrapper; holds all workout, nutrition, and gamification data on-device. |
| Async | **Kotlin Coroutines + Flow** | Reactive queries; UI updates when DB changes. |
| DI | **Hilt** | Standard Android dependency injection. |
| Charts | **Vico** (Compose-native) | Free; renders the 1RM/volume charts RepCount paywalls. |
| Nav | **Navigation-Compose** | Single-Activity, composable destinations. |
| Prefs | **DataStore** | Units (kg/lb), theme, targets. |
| Background | **WorkManager** | Scheduled local backups, streak checks, notifications. |

### 4.2 Module layout (suggested Gradle modules or packages)
```
:app            → navigation host, DI wiring, theme
:core:database  → Room entities, DAOs, migrations, seeders
:core:model     → domain models, enums (ExerciseType, MuscleGroup)
:core:ui        → shared Compose components, design system
:feature:workout    → log, routine, live-session, history
:feature:stats      → analytics + charts (1RM, volume, PR)
:feature:nutrition  → food log, macros, OFF client
:feature:profile    → gamification, levels, stat sheet, achievements
:core:export        → JSON/CSV export & import
```

### 4.3 Offline-first principle
All reads/writes hit Room. No network on the critical path except (a) optional Open Food Facts food lookups and (b) optional future sync. The app must be **fully usable in airplane mode**.

---

## 5. Data model

**Software Architect:** The polymorphic set is the crux (§2.5). Model it as one `SetEntry` table with nullable measurement columns, validated by the parent exercise's `ExerciseType`.

### 5.1 Core entities (workout domain)
```
Exercise
  id, name, muscle_group_id (FK), exercise_type (enum),
  is_unilateral (bool), is_custom (bool), is_archived (bool)

MuscleGroup (== "Category")
  id, name, display_order

ExerciseMuscle          // NEW — enables the body/rank map (RepCount lacks this)
  exercise_id (FK), muscle (enum), role (PRIMARY | SECONDARY),
  contribution_weight (float; PRIMARY ~1.0, SECONDARY ~0.4)
  // one exercise -> many muscles. muscle_group_id above stays as the display
  // "category"; THIS table drives rank aggregation and the body map.

Routine
  id, name, target_mode (enum: LATEST | FIXED), notes, display_order

RoutineExercise
  id, routine_id (FK), exercise_id (FK), position,
  target_sets (int), rep_min (int), rep_max (int)

Workout            // a performed session (a Log entry)
  id, name, date, start_time, end_time, bodyweight_kg,
  notes, source_routine_id (FK, nullable)
  // ALL weights (here + SetEntry.weight) stored canonical in KG.
  // kg/lb is a display-only Setting — convert on render, never on store.

WorkoutExercise
  id, workout_id (FK), exercise_id (FK), position, superset_group_id (nullable)

SetEntry           // POLYMORPHIC — nullable fields gated by ExerciseType
  id, workout_exercise_id (FK), position,
  set_kind (enum: WARMUP | WORKING | DROPSET),
  weight, reps, time_seconds, distance, kcal,   // all nullable
  notes, is_pr (bool, derived)
```

### 5.2 Enums
- `ExerciseType`: the 10 modes in §2.5. Each maps to a set of *required* vs *hidden* `SetEntry` fields (drives UI + validation).
- `SetKind`: `WARMUP`, `WORKING`, `DROPSET`.
- `TargetMode`: `LATEST` (prefill from last performance), `FIXED`.

### 5.3 Nutrition domain
```
FoodItem
  id, name, brand, source (enum: CUSTOM | OFF), barcode (nullable),
  serving_size, serving_unit,
  kcal_per_serving, protein_g, carbs_g, fat_g,
  (optional: fiber_g, sugar_g, sodium_mg), is_favorite

DiaryEntry
  id, date, meal (enum: BREAKFAST | LUNCH | DINNER | SNACK),
  food_item_id (FK), quantity_servings

NutritionTarget
  id, effective_date, kcal_target, protein_target_g, carbs_target_g, fat_target_g
```

### 5.4 Gamification domain
```
ExerciseProgress          // per-exercise leveling
  exercise_id (FK), total_xp, level, best_e1rm, best_volume_set

MuscleGroupRank           // NEW — derived; powers the body/rank map (§7.7)
  muscle (enum), rank (S..E | UNRANKED), score, updated_at
  // score = weighted AVERAGE of normalized best lifts of exercises hitting
  //   this muscle, primary x1.0 vs secondary x0.4 (ExerciseMuscle.weight).
  //   UNRANKED (grey) when no exercise trains it — distinct from rank E.

UserStats                 // aggregated stat sheet
  strength_score, endurance_score, consistency_score, ... , updated_at

Achievement
  id, key, title, description, unlocked_at (nullable)

StreakState
  current_streak_days, longest_streak_days, last_workout_date
```

**QA note:** gamification tables are **derived/cache** data. They must be **fully recomputable** from `Workout`/`SetEntry` history, so a rebuild function can regenerate them (protects against formula changes and corruption). Never treat XP as a source of truth.

---

## 6. Screen-by-screen UX spec

**UI/UX Designer:** Mirror RepCount's proven, low-friction logging IA, but regroup everything by **domain** into three top-level worlds and add the two things RepCount lacks (Nutrition, gamified Profile). A live wireframe of the screens below exists (`nav-schematic.html`).

### 6.1 Navigation (CONFIRMED)
Persistent **bottom navigation bar with 3 destinations — Train · Nutrition · Profile.** No global "mode switch" (an anti-pattern); one thumb tap between domains, a **per-tab back stack** with state preservation, and a persistent **"workout in progress" bar** so switching tabs mid-session never loses the live workout. RepCount's hamburger **drawer is removed** — its utilities move behind a **Settings gear** in Profile's top app bar.

**Where every RepCount surface lands:**

| RepCount surface | New home | Reached via |
|---|---|---|
| Log (history) | **Train** | default view |
| Routines | **Train** | History ⇄ Routines segmented toggle |
| Live logging + rest timer | **Train** | Start Workout / tap a history entry (full-screen) |
| Statistics (was paywalled) | **Profile** | free; alongside rank & charts |
| Edit Exercises / Categories | **Settings** | gear → Exercise Library / Categories |
| Account / Help / Feedback | **Settings** | gear |
| CSV/JSON export (was paywalled) | **Settings** | gear → Export / Import (free) |
| Premium paywall | **removed** | everything is free |

Net-new (not in RepCount): **Nutrition** (tab), **Rank Map + gamification** (Profile, §7.7), **Export/Import** (free).

### 6.2 Log tab
- Date-grouped list of workouts; each card shows name, duration, exercise summary. Tap → workout detail. FAB → new workout (blank or from routine).

### 6.3 Live workout / detail
- Header: name, bodyweight, times, notes, rest timer.
- Exercise sections with sets; **the set row renders only the fields valid for that exercise's type** (e.g. Cardio shows time/distance/kcal, not Kg/Reps).
- Warmup toggle per set; "Add Set"; prefill from last session; per-exercise quick history + mini chart.
- Superset grouping (free in our app).

### 6.4 Routines
- Template list; editor with rep-range targets and `TargetMode`; **START** → instantiates a `Workout` with prefilled targets.

### 6.5 Stats
- Overall totals + per-exercise drilldowns: estimated 1RM curve, volume trend, PR history, frequency. All free.

### 6.6 Nutrition
- Daily view: calorie ring + macro bars vs targets; meals list; add-food (search OFF, favorites, custom, recent). Weekly summary.

### 6.7 Profile (gamification) — see §7
- **Leads with a single overall rank** (hero) + level/XP, then the STR/END/CON stat sheet and the (free) stats/charts.
- A **Rank Map** entry drills into a front/back body map (§7.7); the flat per-exercise tier breakdown is the **bottom layer** of that drill-down, not the landing view.
- Also holds streak, achievements, and the **Settings gear** (top-right).

### 6.8 Design system
- Dark-first (RepCount is dark; our audience expects it), single accent color, large tap targets for gym use (sweaty hands, quick logging), numeric keypads for weight/reps, minimal taps to log a set.

---

## 7. Gamification design ("Solo Leveling" for the gym)

**Game Designer:** The theme is popular, so **the differentiator is the fairness of the math, not the skin.** Design the formulas before writing Kotlin; prototype in a spreadsheet.

### 7.1 Design goals
- Progress must reflect **real training progress**, not time spent or ego lifting.
- Cross-user comparison (for friends) must be **fair across bodyweight and sex**.

### 7.2 Per-exercise leveling (XP)
- XP is awarded primarily for **beating your own records** (estimated 1RM PRs, rep PRs) and secondarily for **consistency**, with **diminishing returns on raw volume** to avoid junk-set farming.
- Estimated 1RM via a standard formula (e.g. **Epley:** `1RM ≈ w × (1 + reps/30)`), documented and consistent app-wide.
- Level curve: increasing XP per level (e.g. quadratic) so early levels feel fast, later ones earned.

### 7.3 Normalized strength stat (DECIDED: IPF GL points, hybridized)
**Game Designer + Requirements Specialist:** To compare a 60 kg and a 100 kg lifter fairly you must normalize for **bodyweight and sex**. We use the **IPF GL ("Goodlift") coefficient** — the modern powerlifting standard that replaced Wilks in 2020 (Wilks systematically under-scored heavier lifters).

- **Formula:** `GL = liftedTotal × 100 / (A − B·e^(−C·BW))`, with sex/equipment-specific `A, B, C` (use the *classic/raw* set), `BW` in kg; ~100 ≈ world-class.
- **Keep it swappable:** put normalization behind an interface so **DOTS** (bodyweight-only polynomial, sex-specific, no weight-class buckets — arguably better for comparing *individuals*) is a drop-in alternative to GL.

**The catch — per-exercise ranks can't use IPF GL directly.** GL/Wilks are defined only for the **squat+bench+deadlift total** (and bench-only); there is no coefficient for a lateral raise. So the model is a **hybrid**:
1. **Overall STR score / global rank** → **IPF GL** computed from the user's best compound lifts.
2. **Per-exercise & per-muscle ranks** (§7.7 tier list / Rank Map) → **per-exercise relative-strength standards** — bodyweight- and sex-normalized target tables per exercise (the Symmetric Strength / StrengthLevel approach: Beginner→Elite bands ≈ our E→S), placing your estimated 1RM against the band.

**Validation before ship:** a known lifter's GL must match public calculators; a handful of exercise standards must match published tables.

### 7.4 Stat sheet (RPG framing)
Derived, recomputable stats, e.g.:
- **STR** — normalized max strength across main lifts.
- **END** — work capacity (volume/rep tolerance).
- **CON(sistency)** — streak & adherence.
- **Rank** (E→S) — a banded aggregate for flavor.

### 7.5 Anti-cheese rules
- Cap XP/session; weight PRs over volume; ignore implausible entries (configurable sanity bounds); allow entries to be flagged "not for records."

### 7.6 Honest risk
**Game Designer (pushback):** gamification retention gains are real but modest and fade if the math feels arbitrary. Budget real design time for the curve/normalization; treat the RPG theme as polish on top of a sound model, not the product itself.

### 7.7 Rank Map (body-map visualization) — the Profile drill-down
Tap the overall rank → **front + back** body figures with each muscle region colored by its `MuscleGroupRank`. This is the Solo-Leveling "status screen" moment, and it's a proven pattern (Fitbod/Musclewiki/Hevy use muscle maps). Semantics are explicit: this map means **strength rank**, *not* recovery/soreness.

- **Aggregation rule (decided):** a muscle's rank = **weighted average** of the normalized best lifts of the exercises training it, with **primary movers weighted higher than secondary** (≈×1.0 vs ×0.4) via `ExerciseMuscle.contribution_weight`. Weighted average is chosen over "max" deliberately — a lagging lift *drags the region down*, which is more honest.
- **Empty state:** a muscle with no logged exercise renders **grey / "not trained"** — a different message from rank **E** ("trained but weak"). Do not conflate them.
- **Color = the rank scale** (S→E), reused from the tier badges so map and tiers speak one language; always pair the hue with the **letter label** for colorblind safety (front/back figure, legend, and per-exercise rows all carry letters).
- **Drill-down:** tap a region → the per-exercise breakdown feeding that muscle (with each exercise's primary/secondary weight shown). This flat per-exercise list is the **bottom layer**, replacing it as a landing view.
- **Data dependency:** requires the `ExerciseMuscle` join (§5.1). RepCount only stores one Category per exercise, so this must be seeded/authored — **bake the mapping into Phase 0** even though the map ships later; retrofitting it is painful.

**Future consideration (noted, not v1):** a **second map mode for recovery / soreness** (Fitbod-style — driven by recent training volume + time since a muscle was last trained), shown on the same body figure as a *separate lens*, clearly distinct from strength rank. The `ExerciseMuscle` model already makes it feasible later.

---

## 8. Nutrition module — the scope-risk area

**Requirements Specialist (pushback):** Manual macro entry is trivial; **usable food data is the expensive part.** Do not build barcode scanning or chase a paid food API in v1.

### 8.1 v1 approach
- **Open Food Facts** (free, open, barcode-capable, but patchy/Euro-centric) for search + a **"my foods"** favorites/custom list + manual entry. Accept imperfect data.
- Cache OFF results locally so repeated foods work offline.

### 8.2 Later
- Barcode scanning (ML Kit) in Phase 4.
- Consider USDA FoodData Central (free, US-centric, high quality, no barcodes) as a supplementary source.
- Paid APIs (Nutritionix/Edamam) only if this ever goes commercial.

### 8.3 Data quality guardrail
Always let the user **override** fetched values and save a corrected custom copy. Never trust third-party macros blindly.

---

## 9. "Sharing with friends" strategy

**Software Architect:** Two staged options — do **not** build a backend for v1.

1. **v1 (no infra):** each person runs their own copy; share progress via **export/import** (JSON for full fidelity, CSV for spreadsheets). Zero cost, zero privacy liability.
2. **Later (only if a live shared leaderboard becomes the point):** a managed backend — **Supabase or Firebase** (auth + Postgres/Firestore, generous free tiers).

**Requirements Specialist (privacy):** the moment you host friends' data you take on a data-protection responsibility even informally (GDPR applies to personal data regardless of scale). Keep any future sync **opt-in, minimal, and deletable**.

---

## 10. Non-functional requirements

| # | Requirement | Acceptance |
|---|---|---|
| NFR-1 | **Offline-first** | Full logging works in airplane mode. |
| NFR-2 | **Data ownership** | One-tap export (JSON + CSV) and import; no feature paywalled. |
| NFR-3 | **Local backup** | Scheduled WorkManager backup to a user-visible folder; restore path tested. |
| NFR-4 | **Migrations** | Every Room schema change ships a tested migration; no data loss on update. |
| NFR-5 | **Derived-data rebuild** | Stats & gamification fully recomputable from raw history. |
| NFR-6 | **Performance** | Set logging tap→persisted < 100 ms; history list scrolls at 60 fps with 1000+ workouts. |
| NFR-7 | **Fat-finger UX** | Numeric keypads, large targets, undo for destructive actions. |
| NFR-8 | **Privacy** | No analytics/telemetry by default; no data leaves device except explicit export or opted-in OFF lookups. |

---

## 11. Phased roadmap

| Phase | Deliverable | Notes |
|---|---|---|
| **0** | Project skeleton: Compose + Room + Hilt + nav; seed exercise catalog & categories. | Establishes schema early. |
| **1** | Workout core: catalog, routines w/ prefill, live logging (polymorphic sets), warmup markers, rest timer, history. | = RepCount free tier. |
| **2** | Stats (free): 1RM, volume, PR detection, per-exercise charts, totals. | = RepCount premium, cheaply. |
| **3** | Gamification: XP/levels, normalized stat sheet, streaks, achievements. **Prototype formulas in a spreadsheet first.** | The design-heavy phase. |
| **4** | Nutrition: manual + OFF search, macros vs targets, favorites. | Barcode later. |
| **5** | Export/import polish; *optional* backend + friends leaderboard. | Only if wanted. |

**Definition of done per phase (QA):** feature works offline, has a migration if schema changed, derived data recomputes correctly, and the primary flow is verified on-device.

---

## 12. Open questions (answer before building past Phase 0)

1. ~~**Units**~~ — **RESOLVED:** kg/lb is an **adjustable Setting**. **Hard rule:** all weights stored **canonical in kg**; the setting is **display-only** (convert on render). This never mutates data, keeps history/PRs stable, and satisfies IPF GL/normalization (which need kg). Avoid storing in the entry-time unit — that corrupts history on toggle. (Drop per-`Workout` `bodyweight_unit`; use one global display pref in DataStore.)
2. ~~**Data import**~~ — **RESOLVED:** **start fresh.** No RepCount history import (`Last_trainings.md` was unrelated; the `w1d1…w3d2` program isn't needed). No importer in v1.
3. ~~**Program integration**~~ — **RESOLVED:** **flat templates only** (RepCount-style) — no periodized week/day/progression model. **Seed one example template** for first-run; users build their own.
4. ~~**Nav count**~~ — **RESOLVED:** 3 bottom-nav tabs — **Train · Nutrition · Profile**. Stats folded into Profile, Routines under Train, utilities behind a Settings gear (§6.1). Rank/tier surfaced via the Rank Map drill-down, not a flat list (§7.7).
5. ~~**Strength-normalization model**~~ — **RESOLVED:** **IPF GL points** (swappable with DOTS) for the overall STR/global rank from compounds; **per-exercise relative-strength standards** for the tier-list/body-map ranks (IPF GL can't cover accessories). Real-number validation still required before ship (§7.3).
6. **Friends sharing:** confirm v1 is export/import only (no accounts), matching the "just me + a few friends" scope.

---

### Appendix A — Sources
- RepCount official site, Google Play listing (`sp.repcount`), RepCount support (subscription rationale). Verified against the live app on a Pixel 9, 2026-07-11.
- Screens captured during study: Log, workout detail, premium paywall, Routines list, routine detail, Statistics (locked), drawer, exercise library, exercise editor, exercise-type taxonomy.
