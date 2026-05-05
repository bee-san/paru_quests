# Paruchan Quest Log

Native Android MVP for a private two-person quest tracker.

The app stores all quest data locally in `filesDir/questlog.json`. Android cloud backup is disabled. There are no accounts, no server, no database, and no built-in AI image generation.

The Files screen includes one bundled starter pack: a single `Thank you paruchan` quest worth `5000 XP`. Importing it merges the quest into local app data just like any other quest pack.

Settings includes daily quest reminders. Reminder notifications are local-only and are posted only when the quest log still has uncompleted available quests.

## Quest Types

Quest packs can include one-off quests, repeatable quests, dailies, counter quests, multi-step goals, and foreground timers:

```json
{
  "title": "Twenty minute tidy",
  "xp": 80,
  "category": "Home",
  "cadence": "daily",
  "goalTarget": 4,
  "goalUnit": "room",
  "timerMinutes": 20
}
```

Multi-step goals award XP when the target is reached. Daily goals reset on the next local day.

Counter quests use `cadence: "counter"` with `xp` as XP per unit. For example:

```json
{
  "title": "Run miles",
  "xp": 100,
  "category": "Fitness",
  "cadence": "counter",
  "goalUnit": "mile"
}
```

Logging `3` miles awards `300 XP`.

The Files screen includes a quest-pack maker. Add quests to a temporary pack, then export the JSON through Android's file picker or share it through the Android share sheet.

## Encrypted Shared Packs

Curated private quest packs can be encrypted and bundled directly into the APK under `app/src/main/assets/shared-packs/`. Paru saves the shared-pack password once in Settings; the app stores it with Android Keystore-backed encryption and auto-imports new bundled packs after APK updates.

To create an encrypted shared pack, put the plaintext pack JSON outside the repo and run:

```bash
bash tools/encrypt_shared_pack.sh /tmp/plain-pack.json app/src/main/assets/shared-packs/<pack-id>.encrypted.json <pack-id> <pack-version>
```

The script reads `agent-skills/paruchan-shared-packs/.env`, which is ignored by git. Commit only encrypted shared-pack assets.

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
