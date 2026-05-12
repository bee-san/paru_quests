# Paruchan Quest Log

Native Android MVP for a private two-person quest tracker.

The app stores all quest data locally in `filesDir/questlog.json`. Android cloud backup is disabled. There are no accounts, no server, no database, and no built-in AI image generation. The app also keeps private daily local snapshots under `filesDir/questlog-backups/`, retaining the newest 10 copies.

The Files screen keeps practical file tools: manual quest-pack import, full backup export, and backup restore. Importing any quest pack makes that pack the current open quest set: older quests are archived without adding completions or XP, while matching quest IDs update in place.

Settings includes backup export and daily quest reminders. Reminder notifications are local-only and are posted only when the quest log still has uncompleted available quests.

## Quest Types

Quest packs can include one-off quests, repeatable quests, dailies, finite counters, timer goals, multi-step goals, and optional foreground timer helpers:

```json
{
  "title": "Twenty minute tidy",
  "xp": 80,
  "category": "Home",
  "cadence": "daily",
  "goalType": "completion",
  "goalTarget": 4,
  "goalUnit": "room",
  "timerMinutes": 20
}
```

Multi-step goals award XP when the target is reached. Daily goals reset on the next local day.

Counter quests use `goalType: "counter"` and complete when the user taps `+1` enough times. `xp` is awarded once when the target is reached:

```json
{
  "title": "Spot 10 paruchans",
  "xp": 100,
  "category": "Adventure",
  "cadence": "once",
  "goalType": "counter",
  "goalTarget": 10,
  "goalUnit": "paruchan"
}
```

Timer goals use `goalType: "timer"` and count manually recorded minutes:

```json
{
  "title": "Practice for twenty minutes",
  "xp": 80,
  "category": "Music",
  "cadence": "once",
  "goalType": "timer",
  "goalTarget": 20
}
```

The `category` field remains accepted and exported for older pack compatibility, but the app does not surface categories as a user-facing organizing concept.

## Encrypted Shared Packs

The APK ships exactly one current encrypted private shared pack at `app/src/main/assets/shared-packs/current.encrypted.json`. Paru saves the shared-pack password once in Settings; the app stores it with Android Keystore-backed encryption and auto-imports the current bundled pack after APK updates.

To replace the encrypted shared pack, put the plaintext pack JSON outside the repo and run:

```bash
bash tools/encrypt_shared_pack.sh /tmp/plain-pack.json app/src/main/assets/shared-packs/current.encrypted.json bedroom-dust-bandits <pack-version>
```

Use the next curated `packVersion`; for example, `v0.1.13` uses pack version `6`. The script reads `agent-skills/paruchan-shared-packs/.env`, which is ignored by git. Commit only the encrypted `current.encrypted.json` asset, never plaintext private packs.

## Build

```bash
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
```

This environment currently needs a valid Android SDK path. If `ANDROID_HOME` points at an empty SDK, run with:

```bash
ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew test lintDebug assembleDebug
```

## Update Repository

The in-app updater reads the latest public GitHub Release from `bee-san/paru_quests` by default. Override it at build time with:

```bash
./gradlew assembleRelease -PupdateRepository=owner/repo
```
