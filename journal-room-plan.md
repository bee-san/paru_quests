# Structured Diary And Room Database Plan

This file is intentionally self-contained. It includes the product decisions, current repo facts, implementation shape, tests, and gotchas needed to start implementation without the original planning conversation.

## Goal

Add a structured daily diary to Paru Quests and move durable quest-log data into a local Room database.

The diary should feel like a small daily ritual, not a generic notes app. It should ask:

- One thing that made Paruchan happy today
- One thing Paruchan is grateful for today
- Tell me one of your favourite memories

The Diary screen should also show one daily reflection from a previous favourite memory or happy moment.

## Planning Context

Original request:

- Add a gratitude journal or small daily diary to Paru Quests.

Decisions made during planning:

- First pass chose a daily diary, not gratitude-only.
- The user specifically added this gratitude wording:
  - `One thing that made Paruchan happy today.`
  - `One thing that Paruchan is grateful for today.`
- The user chose a new tab, not a Home card or folded into the existing Log tab.
- The user chose small daily XP.
- The user then requested a more structured diary, database storage, and this extra prompt:
  - `Tell me one of your favourite memories`
- The user also requested a daily shared reflection:
  - Each day, show a random favourite memory or thing that made Star/Paruchan happy from prior entries.
- Final wording choice was Paruchan, not Star.
- Final reflection location choice was Diary Header.
- Final database scope choice was All App Data.
- Final database layer choice was Room.
- Final backup contract choice was Keep JSON Backups.
- Final migration choice was Auto One-Time from the old `questlog.json`.

## Product Decisions

- Add a new bottom-nav `Diary` tab.
- Use Paruchan wording in prompts.
- Store all durable app data in Room, not just diary entries.
- Keep the app local-only: no server, account, sync, analytics, or cloud backup.
- Keep backup/export/restore as JSON through the existing Android file picker.
- Auto-migrate existing installed users from `filesDir/questlog.json` on first launch.
- Award `10 XP` once per local day when the diary entry is complete.
- Save partial diary drafts with `0 XP`.
- Do not award extra XP when editing the same day's diary entry later.

## Current Codebase Facts

Repo:

- Working directory: `/home/bee/Documents/src/github/paru_quests`
- Native Android app only.
- Single module: `:app`.
- Package/application id: `com.paruchan.questlog`.
- Current app version in `app/build.gradle.kts`: `versionCode = 15`, `versionName = "0.2.0"`.
- Android cloud backup is disabled by XML rules; keep private data local.

Key files:

- `agents.md`: repo instructions and data contract.
- `app/src/main/java/com/paruchan/questlog/MainActivity.kt`: one Activity and Activity Result launchers.
- `app/src/main/java/com/paruchan/questlog/ui/ParuchanQuestLogApp.kt`: main Compose UI and bottom navigation.
- `app/src/main/java/com/paruchan/questlog/ui/QuestLogViewModel.kt`: state coordination and repository calls.
- `app/src/main/java/com/paruchan/questlog/core/Models.kt`: current domain data classes.
- `app/src/main/java/com/paruchan/questlog/core/QuestLogEngine.kt`: XP, level progress, quest completion rules.
- `app/src/main/java/com/paruchan/questlog/core/QuestLogJsonCodec.kt`: backup/current JSON encode/decode/normalize.
- `app/src/main/java/com/paruchan/questlog/data/QuestLogRepository.kt`: current JSON file persistence.
- `app/src/main/java/com/paruchan/questlog/data/BundledSharedPackRepository.kt`: bundled encrypted shared-pack import.
- `app/src/main/java/com/paruchan/questlog/data/SharedPackSecretStore.kt`: Keystore-backed shared-pack password.
- `app/src/main/java/com/paruchan/questlog/notification/QuestReminderNotifier.kt`: reminder notification reads the quest log.

Current persistence:

- Durable quest-log data is currently one JSON file: `filesDir/questlog.json`.
- Automatic daily local backups are JSON snapshots under `filesDir/questlog-backups/`.
- Retention is the newest 10 dated files named like `questlog-YYYY-MM-DD.json`.
- Current backup export/restore uses Android file picker flows.

Current screens:

- `Home`: dashboard, XP level card, active quests, quick actions.
- `Log`: quest completion history.
- `Files`: import quest pack, export backup, restore backup.
- `Prefs`: app update, backup export, reminders, shared packs.

Current quest behavior:

- Total XP is currently only `sum(completions.xpAwarded)`.
- `QuestLogEngine.completeQuest` appends `Completion` records.
- Non-repeatable quests lock after completion.
- Daily quests reset on the next local day.
- Counter/timer/multi-step goals can award `0 XP` before their target is reached.
- Quest-pack imports should update/close quests but preserve completions and XP history.

Shared packs:

- The APK ships exactly one current encrypted private shared pack at `app/src/main/assets/shared-packs/current.encrypted.json`.
- Password stays out of git and app code.
- Password is saved locally through Android Keystore-backed prefs.
- Bundled import markers are currently in `SharedPreferences` inside `BundledSharedPackRepository`.
- In the Room migration, move only the durable imported markers into Room. Do not move the password.

## Database Plan

Use Room `2.8.4` with KSP for Kotlin `2.0.21`.

Gradle changes:

- Add `com.google.devtools.ksp` plugin version `2.0.21-1.0.28`.
- Add Room dependencies:
  - `androidx.room:room-runtime:2.8.4`
  - `androidx.room:room-ktx:2.8.4`
  - `androidx.room:room-compiler:2.8.4` via `ksp`
  - `androidx.room:room-testing:2.8.4` for tests if useful

Create Room entities for:

- `QuestEntity`
- `CompletionEntity`
- `LevelEntity`
- `JournalEntryEntity`
- `SharedPackImportMarkerEntity`
- `AppMetadataEntity`

Keep these outside Room:

- Shared-pack password, which stays in Android Keystore-backed prefs.
- Notification settings, which stay in prefs.
- Temporary UI state.

Suggested table shape:

- `quests`
  - `id TEXT PRIMARY KEY`
  - `title TEXT NOT NULL`
  - `flavourText TEXT NOT NULL`
  - `xp INTEGER NOT NULL`
  - `category TEXT NOT NULL`
  - `icon TEXT NOT NULL`
  - `repeatable INTEGER NOT NULL`
  - `cadence TEXT NOT NULL`
  - `goalType TEXT NOT NULL`
  - `goalTarget INTEGER NOT NULL`
  - `goalUnit TEXT NOT NULL`
  - `timerMinutes INTEGER`
  - `createdAt TEXT NOT NULL`
  - `archived INTEGER NOT NULL`
- `completions`
  - `id TEXT PRIMARY KEY`
  - `questId TEXT NOT NULL`
  - `completedAt TEXT NOT NULL`
  - `xpAwarded INTEGER NOT NULL`
  - `progressAmount INTEGER NOT NULL`
  - `note TEXT`
- `levels`
  - `level INTEGER PRIMARY KEY`
  - `xpRequired INTEGER NOT NULL`
  - `title TEXT NOT NULL`
  - `unlocksJson TEXT NOT NULL`
- `journal_entries`
  - `id TEXT PRIMARY KEY`
  - `localDate TEXT NOT NULL UNIQUE`
  - `happyText TEXT NOT NULL`
  - `gratefulText TEXT NOT NULL`
  - `favoriteMemoryText TEXT NOT NULL`
  - `createdAt TEXT NOT NULL`
  - `updatedAt TEXT NOT NULL`
  - `xpAwarded INTEGER NOT NULL`
- `shared_pack_import_markers`
  - `marker TEXT PRIMARY KEY`
  - `createdAt TEXT NOT NULL`
- `app_metadata`
  - `key TEXT PRIMARY KEY`
  - `value TEXT NOT NULL`

Suggested DAO operations:

- Read all quests ordered by original insertion order or title-compatible stable order. If preserving insertion order matters, add an explicit `position` column to `quests`.
- Upsert/replace all quests, completions, levels, and journal entries transactionally for backup restore.
- Insert/update one journal entry by `localDate`.
- Query recent journal entries by `localDate DESC`.
- Query reflection candidates where `localDate != today` and either `favoriteMemoryText` or `happyText` is nonblank.
- Insert/list shared-pack import markers.
- Read/write app metadata keys.

Room implementation notes:

- Prefer `@Transaction` repository methods around multi-table writes.
- Keep Room entities under `app/src/main/java/com/paruchan/questlog/data/db/`.
- Keep entity-to-domain mappers in a small mapper file near the database code.
- Do not leak Room entities into `core/` or Compose UI.
- Keep `core/` JVM-testable where possible.

## Data Model

Keep the existing core models as the domain/export shape:

- `Quest`
- `Completion`
- `Level`
- `QuestLogState`

Add:

```kotlin
data class JournalEntry(
    var id: String = "",
    var localDate: String = "",
    var happyText: String = "",
    var gratefulText: String = "",
    var favoriteMemoryText: String = "",
    var createdAt: String = "",
    var updatedAt: String = "",
    var xpAwarded: Int = 0,
)
```

Update:

```kotlin
data class QuestLogState(
    var schemaVersion: Int = 2,
    var quests: List<Quest> = emptyList(),
    var completions: List<Completion> = emptyList(),
    var levels: List<Level> = DefaultLevels.paruchan(),
    var journalEntries: List<JournalEntry> = emptyList(),
    var exportedAt: String? = null,
)
```

Total XP becomes:

```text
sum(completions.xpAwarded) + sum(journalEntries.xpAwarded)
```

Quest-pack import should keep affecting only quests and completions. It must not archive, delete, or mutate journal entries.

Journal normalization rules:

- `localDate` is an ISO local date string like `2026-05-12`, using the device local timezone.
- Trim all text fields before saving.
- Store blank draft fields as empty strings, not nulls.
- `xpAwarded` is either `0` or `10` for now.
- A complete journal entry means all three prompt fields are nonblank after trim.
- The first transition from incomplete to complete for a given `localDate` awards `10 XP`.
- Once `xpAwarded` is `10`, keep it `10` forever for that entry, even if later edits clear a field.

## Migration Plan

On first app launch after the database feature ships:

1. Open/create the Room database.
2. Check metadata for `legacy_json_migrated`.
3. If not migrated and `filesDir/questlog.json` exists, decode it with the existing `QuestLogJsonCodec`.
4. Insert quests, completions, levels, and any journal entries into Room in one transaction.
5. Store `legacy_json_migrated = true` only after the transaction succeeds.
6. Leave the old JSON file untouched as a fallback.

If migration fails:

- Keep the database unchanged.
- Show a clear snackbar message.
- Do not mark migration complete.
- Retry on next launch.

Migration should also handle clean installs:

- If there is no JSON file and the database is empty, start from the default `QuestLogState()`.
- Still write metadata marking JSON migration complete so the app does not keep checking forever.

Data migration details:

- Decode old JSON using `QuestLogJsonCodec.decodeState`.
- Normalization should keep old schema `1` state valid.
- Old JSON has no `journalEntries`; default to empty.
- Insert default Paruchan levels if the old JSON has no valid levels.
- Preserve completion IDs and quest IDs exactly.
- Preserve archived quest state exactly.
- Do not delete or rename `questlog.json` during v1 migration. It is the fallback if something goes wrong.

## Repository Changes

Refactor `QuestLogRepository` so callers can keep using high-level operations:

- `load(): QuestLogState`
- `save(state: QuestLogState)`
- `importQuestPack(json: String): ImportResult`
- `restoreBackup(json: String): QuestLogState`
- `exportBackup(): String`

Internally, `QuestLogRepository` should read/write Room instead of a JSON file.

Add journal operations:

- `saveJournalEntry(...)`
- `journalEntryForDate(localDate: LocalDate)`
- `recentJournalEntries(limit: Int)`
- `dailyReflectionForDate(localDate: LocalDate)`

Move shared-pack imported markers from prefs into Room so all durable quest-log state lives in the database. Keep only the password in secure prefs.

Keep automatic daily local backups, but make them export JSON snapshots from the database into `filesDir/questlog-backups/`, retaining the newest 10 dated files.

Compatibility goal:

- Existing callers in `QuestLogViewModel`, `BundledSharedPackRepository`, and `QuestReminderNotifier` should require minimal changes.
- `QuestPackImporter` and `SharedPackImporter` can keep operating on `QuestLogState`; the repository handles persistence before/after.
- Keep `QuestLogJsonCodec` as the canonical import/export JSON boundary.

Important current call sites to update:

- `QuestLogViewModel` currently constructs `QuestLogRepository(File(application.filesDir, "questlog.json"))`.
- `BundledSharedPackRepository` currently receives `QuestLogRepository` and stores markers in prefs.
- `QuestReminderNotifier` currently constructs a repository from `File(appContext.filesDir, "questlog.json")`.

Implementation suggestion:

- Add a `ParuchanDatabase` singleton/factory in `data/db`.
- Add a `QuestLogRepository.create(context: Context)` or a small factory object so Activity/ViewModel/notification code cannot accidentally use the old JSON constructor.
- Keep a test-only repository constructor that can use an in-memory Room database and a temp legacy JSON file.

## Diary UI

Add `Diary` to the bottom navigation.

The Diary screen should include:

- A header card showing today's reflection.
- Three text fields:
  - `One thing that made Paruchan happy today`
  - `One thing Paruchan is grateful for today`
  - `Tell me one of your favourite memories`
- A `Save diary` / `Update diary` button.
- A small status line showing whether today's `10 XP` diary reward has been claimed.
- A recent entries list showing date, short preview, and XP.

Daily reflection behavior:

- Pick from previous entries only, not today's in-progress entry.
- Candidate text is `favoriteMemoryText` plus `happyText`.
- The choice should be deterministic for the local date so it stays stable all day.
- If no previous entries exist, show a gentle empty state instead of fake content.

Suggested Diary layout:

- Header area:
  - Title from the existing `FantasyHeader` can remain `Diary`.
  - First card is today's reflection.
  - Empty reflection copy: `Paruchan will remember something sweet here after a few diary days.`
- Today's entry card:
  - Three stacked multiline text fields.
  - Each field should be comfortable for a sentence or short paragraph.
  - Save button at the bottom.
  - Status copy examples:
    - `Diary reward waiting`
    - `Diary reward claimed: +10 XP`
    - `Saved as a tiny draft`
- Recent entries:
  - Show date.
  - Show one-line or two-line preview from favourite memory first, then happy text.
  - Show `+10 XP` chip only when earned.

Bottom-nav note:

- Adding a fifth item may make the nav more crowded, but the user explicitly chose a new tab.
- Keep labels short: `Home`, `Log`, `Diary`, `Files`, `Prefs`.

## ViewModel Changes

Extend `QuestLogViewModel` with:

- Today's diary draft state.
- Recent journal entries.
- Daily reflection text.
- `saveJournalEntry(happyText, gratefulText, favoriteMemoryText)`.

When saving:

- Trim all fields.
- Save partial entries.
- If all three fields are nonblank and today's entry has not already earned XP, set `xpAwarded = 10`.
- Refresh `state`, level progress, recent entries, and daily reflection.
- Show a short snackbar like `Diary saved` or `Diary saved for 10 XP`.

Suggested UI state:

```kotlin
data class DiaryUiState(
    val todayEntry: JournalEntry = JournalEntry(),
    val recentEntries: List<JournalEntry> = emptyList(),
    val dailyReflection: String? = null,
    val saving: Boolean = false,
)
```

Avoid putting mutable text field state only in the ViewModel if it causes every keystroke to write to disk. A good split is:

- Compose owns unsaved text-field values with `rememberSaveable`.
- ViewModel loads today's saved entry.
- Pressing Save calls `saveJournalEntry`.

## Backup And Restore

Export JSON schema `2`.

Restore should accept:

- Schema `1`: quests, completions, levels, exportedAt. Journal list defaults to empty.
- Schema `2`: quests, completions, levels, journalEntries, exportedAt.

Restore should replace these database tables transactionally:

- quests
- completions
- levels
- journal entries

Restore should not overwrite:

- Shared-pack password
- Notification settings
- Android permission state

Backup JSON shape example:

```json
{
  "schemaVersion": 2,
  "quests": [],
  "completions": [],
  "levels": [],
  "journalEntries": [
    {
      "id": "journal-2026-05-12",
      "localDate": "2026-05-12",
      "happyText": "Paruchan had a soft little win.",
      "gratefulText": "Paruchan is grateful for a quiet evening.",
      "favoriteMemoryText": "A cosy walk after dinner.",
      "createdAt": "2026-05-12T20:00:00Z",
      "updatedAt": "2026-05-12T20:00:00Z",
      "xpAwarded": 10
    }
  ],
  "exportedAt": "2026-05-12T20:05:00Z"
}
```

Restore validation:

- Reject non-object backups.
- Reject missing required arrays for the declared schema.
- For schema `1`, require old required fields and default `journalEntries = emptyList()`.
- For schema `2`, require `journalEntries` array.
- Keep human-readable error messages consistent with existing tests.

Daily local backups:

- Existing behavior backs up the local JSON file before/around saves.
- After Room migration, daily backup should call `exportBackup()` and write that JSON to the dated backup file.
- It should still keep only 10 newest snapshots.

## Testing

Add or update JVM tests for:

- Schema `1` backup restore still works and yields empty journal entries.
- Schema `2` backup round trip preserves journal entries.
- Total XP includes completion XP plus diary XP.
- Saving a partial diary entry awards `0 XP`.
- Completing all three diary prompts awards `10 XP`.
- Editing the same day does not award more XP.
- A new local day can earn a new `10 XP` reward.
- Daily reflection is deterministic for the same local date.
- Daily reflection ignores today's entry.
- Quest-pack import preserves journal entries.
- JSON migration inserts existing quests, completions, and levels into Room once.
- Failed migration does not mark migration complete.
- Restore replaces database state transactionally.

Run:

```bash
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
```

Use the repo's Android SDK workaround if needed:

```bash
ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew test lintDebug assembleDebug
```

Existing tests likely to update:

- `BackupRoundTripTest`
- `QuestLogEngineTest`
- `SharedPackImporterTest`
- `QuestPackImportFlowTest`
- Any tests that construct `QuestLogRepository(File(...))`

New tests to add:

- `JournalEntryRepositoryTest`
- `QuestLogDatabaseMigrationTest`
- `RoomQuestLogRepositoryTest`

If Room tests cannot run as plain JVM tests:

- Prefer Robolectric only if already acceptable to add.
- Otherwise keep pure mapping/engine tests as JVM tests and add Android instrumentation tests for Room.
- Do not skip database tests entirely; this change is persistence-heavy.

## Failure Modes And Guardrails

- Do not model diary XP as a fake quest. Quest-pack imports archive quests, so fake diary quests would be brittle.
- Do not store the shared-pack password in Room.
- Do not remove the old JSON file during initial migration.
- Do not make daily reflections random on every recomposition; they must stay stable for a given date.
- Do not show today's unsaved diary text as today's reflection.
- Do not lose existing quest completions or XP during migration.
- Do not change package id or signing assumptions.
- Do not add server sync, accounts, analytics, cloud backup, or image generation.
- Do not commit plaintext shared-pack JSON or secrets.

## Acceptance Criteria

- Existing installed data from `filesDir/questlog.json` appears normally after update.
- Existing quest import/export/restore behavior still works.
- Existing bundled shared-pack auto-import still works with saved password.
- Diary tab can save partial drafts.
- Completing all three diary prompts awards exactly `10 XP` once for that local date.
- Total XP and level progress include diary XP.
- Diary header shows the same reflection all day and changes only by date.
- JSON backup export includes journal entries.
- JSON backup restore can restore journal entries onto a fresh install.
- Daily local backups continue to exist under `questlog-backups` with 10-file retention.
- `./gradlew test lintDebug assembleDebug` passes, or any environment-only blocker is documented clearly.

## Implementation Order

1. Add Room/KSP dependencies and create the database/entities/DAOs.
2. Add mappers between Room entities and core models.
3. Refactor `QuestLogRepository` to use Room while preserving its public behavior.
4. Add one-time JSON migration from `filesDir/questlog.json`.
5. Add `JournalEntry` model and diary operations.
6. Update XP calculation and backup codec for schema `2`.
7. Move shared-pack import markers into Room.
8. Add ViewModel diary state/actions.
9. Add the Diary screen and bottom-nav item.
10. Add tests, then run verification.

Detailed first-day checklist:

1. Read `agents.md`.
2. Check `git status --short --branch`.
3. Add Room/KSP Gradle dependencies.
4. Run `./gradlew test` once after dependency setup to catch toolchain issues early.
5. Add database entities/DAOs and compile.
6. Add mappers and repository tests before touching UI.
7. Refactor repository call sites.
8. Add journal engine/reward tests.
9. Add UI last.
10. Run full verification.

## External References

- Room release notes: https://developer.android.com/jetpack/androidx/releases/room
- KSP Gradle plugin for Kotlin 2.0.21: https://central.sonatype.com/artifact/com.google.devtools.ksp/com.google.devtools.ksp.gradle.plugin/2.0.21-1.0.28
