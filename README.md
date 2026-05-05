# Paruchan Quest Log

Native Android MVP for a private two-person quest tracker.

The app stores all quest data locally in `filesDir/questlog.json`. There are no accounts, no server, no bundled quest data, no database, and no built-in AI image generation.

## Quest Types

Quest packs can include one-off quests, repeatable quests, dailies, multi-step goals, and foreground timers:

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

## Build

```bash
./gradlew test
./gradlew assembleDebug
```

This environment currently needs a valid Android SDK path. If `ANDROID_HOME` points at an empty SDK, run with:

```bash
ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew test assembleDebug
```

## Update Repository

The in-app updater reads the latest public GitHub Release from `bee-san/paru_quests` by default. Override it at build time with:

```bash
./gradlew assembleRelease -PupdateRepository=owner/repo
```
