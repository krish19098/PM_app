# PM Bible

A calm, offline-first Android app that teaches Product Management one day at a time — from zero to advanced, including AI product management — with adaptive daily sessions, spaced revision, real practice and interview preparation, in simple English and Thanglish.

## What's in the app

| Area | What it does |
|---|---|
| **Onboarding** | Name, starting point (new / basics / experienced placement), goal, daily minutes (5–60), pace, study days, weekly goal, preferred time, reminders (0–6), quiet hours, nudge tone, language, interests. |
| **Today** | An adaptive plan for the time you pick (5/10/15/30/45/60 min): due reviews (capped so new learning still happens), prerequisite refreshers, the next lesson split at natural part boundaries, unit challenges, deep dives, interview practice and cases when there's time. Weekly-goal dots (no streaks), catch-up nudges, continue card, weak-area card. |
| **Session player** | Quick Learn → knowledge check → application exercise → Deep Dive, plus reviews, unit challenges, case studies and interview practice. Per-option feedback explaining *why*; "I guessed" flag; progress saved after every step; leaving mid-session loses nothing. |
| **Learn** | 14 units / 56 concepts in prerequisite order, completion vs mastery per lesson, unit mastery, cumulative unit challenges, honest coverage note. |
| **Concept page** | Quick Learn and progressively revealed Deep Dive (why it exists, how it works, when to use or not, limitations, interview angle, counterexample, mistakes, key terms), English / Thanglish / both, concept graph (builds on, unlocks, related, cases, interview questions, challenge), bookmark, personal notes. |
| **Library** | Full-text search across concepts, 81 glossary terms, cases, interview questions and updates; browse by unit; glossary A–Z. |
| **Practice** | Interview Studio (40 questions, filters by category/level/role/company, answer-first, optional 5-minute timer, keyword structure check, suggested approach, pitfalls, follow-ups, rubric self-rating, retry), 12 hypothetical case studies, 14 unit challenges, curated industry updates. |
| **Progress** | Weekly recap, last-4-weeks activity, mastery distribution, prerequisite gaps and weakest concepts, mastery by unit, interview competency by category, Saved (bookmarks + notes), History (answers, exercises, interviews). |
| **Reminders** | Lesson / review / accountability-nudge reminders that respect study days and quiet hours, re-check at fire time (never nag after you've learned today), open the right screen, survive reboot, app updates and time-zone changes; Sunday recap; test notification; MIUI/HyperOS battery guidance. |
| **Settings** | Everything above is editable; theme (system/light/dark), text size, reset progress. |

**Learning engine** (in `:core`, unit-tested):
- *Spaced repetition*: Leitner boxes (1, 3, 7, 16, 35 days). Early or same-day repeats never promote, so cramming can't fake mastery.
- *Mastery ≠ completion*: recall strength decays when reviews are overdue; full mastery also needs application evidence (exercises, cases, challenges, interviews).
- *Prerequisite-gap detection*: struggling with a lesson whose prerequisite looks weak triggers a targeted refresher.
- *Accountability*: misses are counted only on your planned days; progress is never reset; tone-specific, optional light-hearted warnings; welcome-back plan after long breaks.

**Content integrity**: company-tagged interview questions are labelled "Representative practice question — not a verified company question"; all cases are labelled hypothetical; industry updates carry publication dates, source links and a "curated snapshot, not a live feed" notice. A content validator (run in tests) rejects broken references, prerequisite cycles, duplicate ids, malformed questions, and missing Thanglish or feedback.

## Build and run

Requirements: Android Studio Ladybug (2024.2) or newer, or JDK 17+ with the Android SDK (platform 35). Internet access to `google()` and Maven Central for the first build.

```bash
./gradlew :app:assembleDebug          # build the APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug           # install on a connected device (USB debugging on)
./gradlew test                        # JVM tests: :core logic/content + :app Robolectric tests
./gradlew :app:assembleRelease        # minified release (signed with the debug key for personal use)
```

Or open the folder in Android Studio and press **Run**.

**Xiaomi / Redmi / POCO**: to make reminders reliable, open *Settings › Apps › PM Bible* and enable **Autostart**, and set **Battery saver → No restrictions**. The app links there from Settings. Android may still deliver reminders a few minutes late; learning never depends on them.

### Offline verification (no Google Maven)

`tools/offline-check` is a separate Gradle build that needs only Maven Central. It runs the `:core` tests and type-checks the app's Kotlin sources against Compose Multiplatform desktop libraries plus androidx signature stubs. It does not build an APK.

```bash
cd tools/offline-check
gradle :core:test :uicheck:compileKotlin   # optionally -PandroidJar=<sdk>/platforms/android-35/android.jar
```

## Project structure

```
core/                      Pure Kotlin (JVM) — no Android dependencies, fully unit-tested
  model/Content.kt         Content schema (units, lessons, questions, cases, interview, glossary, updates)
  model/Progress.kt        Learner state and preferences
  content/                 ContentParser, ContentValidator (editorial quality gate)
  domain/SessionPlanner.kt Adaptive daily plan from available time
  domain/Mastery.kt        Spaced repetition, mastery, prerequisite gaps
  domain/Accountability.kt Weekly status and nudges
  domain/ReminderPlanner.kt Reminder timing and fire-time rules
  domain/Search.kt, Insights.kt, Placement.kt
app/
  src/main/assets/content/ Curriculum JSON (manifest, 14 unit files, cases, interview, glossary, updates)
  data/                    Room database, DataStore preferences, repositories
  notifications/           AlarmManager scheduling, receiver, notification posting
  ui/                      Compose screens: today, session, learn, library, practice, progress, settings, onboarding
tools/offline-check/       Maven-Central-only verification build (see above)
```

### Editing content

Content is versioned JSON in `app/src/main/assets/content/`. To add a lesson, add it to a unit file with a stable new `id`; to add a unit, create a file and list it in `manifest.json`. Learner progress is stored by id, so edits never reset progress — don't rename ids that have shipped. Run `./gradlew :core:test` (or the offline check): `ContentTest` validates every file.

## Testing status

- **Run and passing here:** 44 JVM tests in `:core` (content validation, spaced repetition, mastery, gap detection, planner across 5–40 minute budgets and full-curriculum progression, placement, reminder timing incl. quiet hours/weekends/DST, accountability messaging, search, answer review, weekly recap).
- **Type-checked here:** all app Kotlin sources (422 classes) against Compose Multiplatform 1.7.3 + Material 3 with androidx stubs.
- **Not run here:** the Android build itself, `app/src/test` Robolectric tests (`ProgressRepositoryTest`, `AppFlowTest`), and any emulator or device testing. The development environment's network policy blocks Google's Maven repository and SDK downloads, so these need a normal Android setup. Run `./gradlew test assembleDebug` first.

## Limitations

- No AI tutor ("Ask PM anything") or live news feed: both need a secure backend and a paid AI API key, which are deliberately not built into the app. Library search covers the bundled content instead.
- Industry updates are a curated, dated snapshot (last reviewed 2026-10-25), not live.
- The interview "structure check" is a transparent keyword heuristic, not AI grading; scores are self-rated against rubrics.
- Thanglish covers each lesson's explanation, analogy and takeaway; deep dives, questions and cases are in English.
- 56 concepts give a broad foundation, not the whole discipline; the content model is built to grow.
- Reminders are inexact by design (battery-friendly alarms); some manufacturers can suppress them unless the app is exempted from battery optimisation.
