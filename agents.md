# Agent Guide

This repo is the Android MVP for Paruchan Quest Log, a private two-person quest tracker. Treat this file as the first place to read before changing code.

## Product Scope

- Target: native Android APK only.
- V1 is tracker-only: no accounts, no server, no remote sync, and no built-in AI/image generation.
- Curated private quest packs may be shipped as encrypted APK assets under `app/src/main/assets/shared-packs/`; this is not remote sync and must not include GitHub tokens or accounts.
- The APK ships exactly one current private shared pack asset: `app/src/main/assets/shared-packs/current.encrypted.json`; plaintext source packs must stay outside git.
- Do not ship public starter-pack assets or retired encrypted shared-pack assets in future APKs.
- Quest data is private user data. It lives only in local app storage or user-imported/exported JSON files; Android cloud backup stays disabled. The app may prompt users to keep app data on uninstall and can write optional backups to a user-picked folder.
- The stable Android package/application id is `com.paruchan.questlog`; do not change it casually because sideloaded updates depend on package and signing continuity.
- Canonical paruchan reference: `/home/bee/Downloads/paruchan.jpg`. Paruchans are soft white plush blobs with rounded cat ears, blue embroidered eyes, a pink nose, and pink cheek/whisker stripes. Do not draw them as generic fantasy cats, do not add smiles, and do not add separate arms, paws, or feet.

## Architecture

- Single Android app module: `:app`.
- One Activity: `app/src/main/java/com/paruchan/questlog/MainActivity.kt`.
- UI is Jetpack Compose in `app/src/main/java/com/paruchan/questlog/ui/ParuchanQuestLogApp.kt`.
- State coordination is in `QuestLogViewModel.kt`.
- Core JVM-testable logic lives under `app/src/main/java/com/paruchan/questlog/core/`.
- Local persistence is `QuestLogRepository`, backed by Room at `filesDir` with one-time migration from legacy `filesDir/questlog.json`.
- Release update logic is in `app/src/main/java/com/paruchan/questlog/update/GitHubReleaseUpdate.kt`.

## Data Contract

- `Quest`: `id`, `title`, `flavourText`, `xp`, `category`, `icon`, `repeatable`, `cadence`, `goalType`, `goalTarget`, `goalUnit`, optional `timerMinutes`, `createdAt`, `archived`.
- `Completion`: `id`, `questId`, `completedAt`, `xpAwarded`, `progressAmount`, optional `note`.
- `Level`: `level`, `xpRequired`, `title`, `unlocks`.
- `JournalEntry`: `id`, `localDate`, `happyText`, `gratefulText`, `favoriteMemoryText`, `createdAt`, `updatedAt`, `xpAwarded`.
- `QuestLogState`: `schemaVersion`, `quests`, `completions`, `levels`, `journalEntries`, optional `exportedAt`.
- Total XP is always derived as `sum(completions.xpAwarded) + sum(journalEntries.xpAwarded)`. Do not add a stored mutable total XP field.
- Non-repeatable quests become unavailable after their first completion.
- Repeatable quests remain available and append another completion each time.
- Daily quests use `cadence: "daily"` and become unavailable after today's goal target is reached; they reset on the next local day.
- `cadence` is only schedule: `once`, `daily`, or `repeatable`.
- `goalType` is goal shape: `completion`, `counter`, or `timer`.
- Counter quests use `goalType: "counter"` and append `progressAmount: 1` each time the user taps `+1`; `xp` is awarded once when `goalTarget` is reached.
- Timer quests use `goalType: "timer"` and append manually recorded minutes as `progressAmount`; `xp` is awarded once when the required minutes are reached.
- Legacy imports with `cadence: "counter"` or `counter: true` must normalize to `cadence: "once"` and `goalType: "counter"`.
- Multi-step goals use `goalTarget > 1`; progress entries can award `0 XP` until the target is reached, then the quest XP is awarded.
- `timerMinutes` is only a foreground helper for non-timer goals and does not run as a background service in v1.
- Default level curve is in `DefaultLevels.kt`; preserve the tested threshold `3550 XP -> Level 7, Visa Bard of Babsy`.

## Import, Export, Restore

- Quest-pack import uses Android's system file picker and accepts JSON arrays or objects with a `quests` array.
- The Files screen keeps manual quest-pack import, full backup export, backup restore, and optional user-picked folder backups. Do not restore the in-app quest-pack maker or built-in starter-pack button unless explicitly requested.
- Quest-pack import makes the imported pack the current open quest set.
- Existing quests that are not present in the imported pack are archived without adding completions or awarding XP.
- If an imported quest has `id`, update by `id`.
- If no `id` is present, derive a stable ID from normalized title and goal shape to avoid duplicates on re-import. Do not include `category`; keep recognizing old category-derived implicit IDs as an update fallback.
- `category` remains part of the JSON contract for backward compatibility, but the app should not surface or rely on categories for current user-facing workflows.
- Encrypted shared packs use `kind: "paruchan.encrypted-quest-pack"` with PBKDF2-HMAC-SHA256 and AES-256-GCM. The decrypted payload is the existing quest-pack JSON format.
- Bundled shared-pack import reads only `shared-packs/current.encrypted.json`. Tests must fail if additional `shared-packs/` assets or old `quest-packs/` starter assets are present.
- The current pack should archive retired active quests on-device while preserving completions, XP totals, and partial progress records.
- The shared-pack password is entered in Settings, stored locally with an Android Keystore key, and used to auto-import bundled shared packs on app launch after an APK update.
- Do not hardcode shared-pack passwords in app code, docs, tests, or committed assets. The local ignored env file is `agent-skills/paruchan-shared-packs/.env`; the tracked example is `.env.example`.
- Use `agent-skills/paruchan-shared-packs/SKILL.md` and `tools/encrypt_shared_pack.sh` when replacing the private shared pack. Keep plaintext packs outside git, preferably under `/tmp`, and commit only `app/src/main/assets/shared-packs/current.encrypted.json`.
- Full backup export writes the whole app state JSON through `ACTION_CREATE_DOCUMENT`.
- Full backup restore replaces the whole state only after user confirmation.
- Backups include `levels` so future custom curves can be edited/imported without a v1 level designer UI.
- Automatic daily local backups are JSON exports from the Room database under `filesDir/questlog-backups/`; keep only the newest 10 dated `questlog-YYYY-MM-DD.json` snapshots.
- Optional folder backups use Android's folder picker, persist the selected tree permission, write `paruchan-quest-log-latest.json`, and keep the newest 10 dated `paruchan-quest-log-YYYY-MM-DD.json` files. Delete only those dated app-created backup filenames during pruning.

## Updater And Release Assumptions

- Settings has a "Check for update" flow.
- The updater reads the latest public GitHub Release for `BuildConfig.UPDATE_REPOSITORY`.
- Default update repo is `bee-san/paru_quests`; override with `-PupdateRepository=owner/repo`.
- It compares release tag against `BuildConfig.VERSION_NAME`, selects a release APK asset, downloads it, verifies a `.sha256` sidecar when present, and opens Android's package installer.
- Encrypted shared-pack updates ride inside the APK. After Paru installs an update and opens the app, saved-password auto-import picks up new bundled assets.
- The app declares `INTERNET`, `REQUEST_INSTALL_PACKAGES`, and a `FileProvider`.
- Do not embed GitHub tokens. Releases must be public or otherwise available through unauthenticated URLs.
- Android cannot silently update sideloaded APKs; keep the explicit installer/unknown-sources UX.
- Release APKs must be signed with the same stable key across versions.

## Build And Test

Use the repo wrapper:

```bash
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
```

Useful combined verification:

```bash
./gradlew --no-daemon test lintDebug assembleDebug
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
- Quest-pack validation, current-pack closeout, and duplicate prevention.
- Encrypted shared-pack decryption, wrong-password failure, idempotent import markers, and quest updates preserving completions.
- Backup export/restore round trip.
- Room migration from legacy `questlog.json`, journal rewards/reflections, and schema 1/schema 2 backup compatibility.
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

- Pull-request and `main` CI run unit tests, Android lint, and debug APK assembly as separate jobs. The unit-test and lint jobs upload their reports, and the debug build uploads the APK artifact.
- CodeQL runs for Java/Kotlin on pull requests, pushes to `main`, weekly schedule, and manual dispatch. It uses the `security-extended` and `security-and-quality` query suites and manually builds `testDebugUnitTest assembleDebug` so Android/Kotlin sources are captured.
- The CodeQL workflow job passing is not the whole code-scanning result. Also check the GitHub Advanced Security `CodeQL` check run for new alerts; it can report alerts even when the Actions workflow succeeds.
- SonarCloud is enabled externally through the SonarQubeCloud GitHub App, not repo-local Sonar workflow files or `sonar-project.properties`. Do not assume Sonar is absent just because no Sonar config exists in git.
- A SonarCloud Quality Gate can pass while code smells remain. For Sonar cleanup, query the SonarCloud API and verify the issue count, for example `https://sonarcloud.io/api/issues/search?componentKeys=bee-san_paru_quests&pullRequest=<pr>&issueStatuses=OPEN,CONFIRMED&ps=500`.
- If asked to fix all Sonar issues, query the project-wide backlog with `componentKeys=bee-san_paru_quests&issueStatuses=OPEN,CONFIRMED&ps=500`, fix issues in source, and avoid accepting/suppressing issues unless explicitly requested. Project-wide `main` results only refresh after the fix is merged and Sonar analyzes `main`.
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
- Keep persistence local-only. Room is the durable store; JSON remains the import/export and backup boundary.
- Do not introduce accounts, remote sync, servers, analytics, or generated image/poster features unless the product scope explicitly changes.
- Prefer small, direct additions that preserve the current app shape.
- Add or update unit tests for behavior changes in import, backup/restore, level calculation, updater selection, or completion rules.
- Keep UI controls functional and compact. This is an operational tracker, not a landing page.
