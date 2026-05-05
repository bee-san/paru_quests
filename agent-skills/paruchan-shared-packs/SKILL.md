---
name: paruchan-shared-packs
description: Encrypt curated Paruchan Quest Log quest packs for bundling inside the APK. Use when adding or updating private shared packs under app/src/main/assets/shared-packs.
---

# Paruchan Shared Packs

Use this skill when the user wants to add or update encrypted shared quest packs.

## Workflow

1. Keep plaintext private pack JSON outside git, preferably under `/tmp`.
2. Load `agent-skills/paruchan-shared-packs/.env`; create it from `.env.example` if missing.
3. Run:

```bash
bash tools/encrypt_shared_pack.sh /tmp/plain-pack.json app/src/main/assets/shared-packs/<pack-id>.encrypted.json <pack-id> <pack-version>
```

4. Commit only the encrypted asset, app code, and docs. Never commit `.env` or plaintext private packs.
5. Bump the APK version and publish a normal GitHub release so Paru receives the encrypted pack through the app update.

Use stable quest IDs in plaintext packs. Reusing an ID updates the quest on import while keeping local completions intact.
