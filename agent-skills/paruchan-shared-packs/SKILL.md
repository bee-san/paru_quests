---
name: paruchan-shared-packs
description: Encrypt curated Paruchan Quest Log quest packs for bundling inside the APK. Use when adding or updating private shared packs under app/src/main/assets/shared-packs.
---

# Paruchan Shared Packs

Use this skill when the user wants to add or update encrypted shared quest packs.

The APK ships exactly one current encrypted shared pack:
`app/src/main/assets/shared-packs/current.encrypted.json`. Do not add
pack-id-specific encrypted assets or keep retired shared-pack assets in the APK.
`packVersion` is still the curated-pack generation number inside that one asset,
not a per-quest update number.

Pack descriptions, flavour text, and similar copy must stay cutesy, warm, and
positive. Do not use negative phrasing, harsh wording, or gloomy descriptions.

## Workflow

1. Keep plaintext private pack JSON outside git, preferably under `/tmp`.
2. Load `agent-skills/paruchan-shared-packs/.env`; create it from `.env.example` if missing.
3. Decrypt or inspect `app/src/main/assets/shared-packs/current.encrypted.json` metadata and choose the next `packVersion`.
4. Run:

```bash
bash tools/encrypt_shared_pack.sh /tmp/plain-pack.json app/src/main/assets/shared-packs/current.encrypted.json <pack-id> <pack-version>
```

5. After encryption, decrypt or inspect `current.encrypted.json` again and confirm the intended `packId`, `packVersion`, pack name, and quest list.
6. Commit only the encrypted asset, app code, and docs. Never commit `.env` or plaintext private packs.
7. Bump the APK version and publish a normal GitHub release so Paru receives the encrypted pack through the app update.

Use stable quest IDs in plaintext packs. Reusing an ID within the current pack updates the quest on import while keeping local completions intact. Replacing the current pack archives quests outside the new pack on-device, without awarding XP and without deleting completion or partial-progress history.
