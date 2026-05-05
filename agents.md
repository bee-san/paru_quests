# Agent Guide

This repo is the Android MVP for Paruchan Quest Log, a private two-person quest tracker. Treat this file as the first place to read before changing code.

## Product Scope

- Target: native Android APK only.
- V1 is tracker-only: no accounts, no server, no database, no bundled quest data, and no built-in AI/image generation.
- Quest data is private user data. It lives only in local app storage or user-imported/exported JSON files.
- The stable Android package/application id is `com.paruchan.questlog`; do not change it casually because sideloaded updates depend on package and signing continuity.
- Canonical paruchan reference: `/home/bee/Downloads/paruchan.jpg`. Paruchans are soft white plush blobs with rounded cat ears, blue embroidered eyes, a pink nose, and pink cheek/whisker stripes. Do not draw them as generic fantasy cats, do not add smiles, and do not add separate arms, paws, or feet.

## Architecture

- Single Android app module: `:app`.
- One Activity: `app/src/main/java/com/paruchan/questlog/MainActivity.kt`.
- UI is Jetpack Compose in `app/src/main/java/com/paruchan/questlog/ui/ParuchanQuestLogApp.kt`.
- State coordination is in `QuestLogViewModel.kt`.
- Core JVM-testable logic lives under `app/src/main/java/com/paruchan/questlog/core/`.
- Local persistence is `QuestLogRepository`, backed by one JSON file at `filesDir/questlog.json`.
- Release update logic is in `app/src/main/java/com/paruchan/questlog/update/GitHubReleaseUpdate.kt`.

## Data Contract

- `Quest`: `id`, `title`, `flavourText`, `xp`, `category`, `icon`, `repeatable`, `cadence`, `goalTarget`, `goalUnit`, optional `timerMinutes`, `createdAt`, `archived`.
- `Completion`: `id`, `questId`, `completedAt`, `xpAwarded`, `progressAmount`, optional `note`.
- `Level`: `level`, `xpRequired`, `title`, `unlocks`.
- `QuestLogState`: `schemaVersion`, `quests`, `completions`, `levels`, optional `exportedAt`.
- Total XP is always derived as `sum(completions.xpAwarded)`. Do not add a stored mutable total XP field.
- Non-repeatable quests become unavailable after their first completion.
- Repeatable quests remain available and append another completion each time.
- Daily quests use `cadence: "daily"` and become unavailable after today's goal target is reached; they reset on the next local day.
- Multi-step goals use `goalTarget > 1`; progress entries can award `0 XP` until the target is reached, then the quest XP is awarded.
- Timed quests use `timerMinutes`; timers are foreground UI helpers and do not run as background services in v1.
- Default level curve is in `DefaultLevels.kt`; preserve the tested threshold `3550 XP -> Level 7, Visa Bard of Babsy`.

## Import, Export, Restore

- Quest-pack import uses Android's system file picker and accepts JSON arrays or objects with a `quests` array.
- Import merges into existing quests.
- If an imported quest has `id`, update by `id`.
- If no `id` is present, derive a stable ID from normalized `title/category/xp/flavourText/repeatable` to avoid duplicates on re-import.
- Full backup export writes the whole app state JSON through `ACTION_CREATE_DOCUMENT`.
- Full backup restore replaces the whole state only after user confirmation.
- Backups include `levels` so future custom curves can be edited/imported without a v1 level designer UI.

## Updater And Release Assumptions

- Settings has a "Check for update" flow.
- The updater reads the latest public GitHub Release for `BuildConfig.UPDATE_REPOSITORY`.
- Default update repo is `bee-san/paru_quests`; override with `-PupdateRepository=owner/repo`.
- It compares release tag against `BuildConfig.VERSION_NAME`, selects a release APK asset, downloads it, verifies a `.sha256` sidecar when present, and opens Android's package installer.
- The app declares `INTERNET`, `REQUEST_INSTALL_PACKAGES`, and a `FileProvider`.
- Do not embed GitHub tokens. Releases must be public or otherwise available through unauthenticated URLs.
- Android cannot silently update sideloaded APKs; keep the explicit installer/unknown-sources UX.
- Release APKs must be signed with the same stable key across versions.

## Build And Test

Use the repo wrapper:

```bash
./gradlew test
./gradlew assembleDebug
```

Useful combined verification:

```bash
./gradlew --no-daemon test assembleDebug
```

Debug APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The test suite covers:

- Level calculation thresholds.
- Completion log XP totals.
- Repeatable quest completion.
- Non-repeatable lockout.
- Quest-pack validation, merge, and duplicate prevention.
- Backup export/restore round trip.
- GitHub release version comparison and APK asset selection.

## Local Environment Notes

- This machine's default `ANDROID_HOME` may point at an empty SDK.
- A local ignored `local.properties` can point Gradle at the installed SDK:

```properties
sdk.dir=/home/bee/Documents/src/github/thaiwrite/.android-sdk
```

- If Gradle fails with native services or file-lock listener errors inside the sandbox, rerun the same `./gradlew` command with escalation. The approved command prefix is typically `./gradlew`.
- Global `gradle -v` may fail with the default Gradle user cache; prefer `./gradlew`.
- This directory currently has an empty read-only `.git` directory, so `git status` may fail with "not a git repository". Verify repository state before promising commits.

## CI Release Flow

- Workflow: `.github/workflows/android-release.yml`.
- Trigger: tags matching `v*`.
- CI installs Android SDK platform/build-tools 36, builds `assembleRelease`, writes one `.sha256` file per APK, and publishes assets to the GitHub Release.
- Required secrets:
  - `ANDROID_KEYSTORE_BASE64`
  - `ANDROID_KEYSTORE_PASSWORD`
  - `ANDROID_KEY_ALIAS`
  - `ANDROID_KEY_PASSWORD`
- The workflow intentionally fails when signing secrets are absent, because installable in-app updates require stable signing.

## Coding Guidelines

- Keep core quest logic JVM-testable and out of Compose where practical.
- Keep persistence JSON-backed unless the data model grows enough to justify Room/SQLite.
- Do not introduce accounts, remote sync, servers, analytics, or generated image/poster features unless the product scope explicitly changes.
- Prefer small, direct additions that preserve the current app shape.
- Add or update unit tests for behavior changes in import, backup/restore, level calculation, updater selection, or completion rules.
- Keep UI controls functional and compact. This is an operational tracker, not a landing page.
