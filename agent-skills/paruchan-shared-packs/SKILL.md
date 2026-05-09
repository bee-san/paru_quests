---
name: paruchan-shared-packs
description: Encrypt curated Paruchan Quest Log quest packs for bundling inside the APK. Use when adding or updating private shared packs under app/src/main/assets/shared-packs.
---

# Paruchan Shared Packs

Use this skill when the user wants to add or update encrypted shared quest packs.

`packVersion` is a global curated-pack generation across all bundled shared
packs, not a per-`packId` update number. At runtime, only bundled assets with
the highest `packVersion` are treated as the current active generation. Multiple
assets can share the highest generation and coexist; lower generations stay in
the APK but are ignored for the active open quest set.

## Workflow

1. Keep plaintext private pack JSON outside git, preferably under `/tmp`.
2. Load `agent-skills/paruchan-shared-packs/.env`; create it from `.env.example` if missing.
3. Before creating a new curated pack, inspect the bundled shared packs by decrypting them or reading their encrypted metadata. Choose `max(packVersion) + 1` for the new current generation.
4. Run:

```bash
bash tools/encrypt_shared_pack.sh /tmp/plain-pack.json app/src/main/assets/shared-packs/<pack-id>.encrypted.json <pack-id> <pack-version>
```

5. After encryption, decrypt or list the bundled shared packs again and confirm the intended current pack has the highest generation.
6. Commit only the encrypted asset, app code, and docs. Never commit `.env` or plaintext private packs.
7. Bump the APK version and publish a normal GitHub release so Paru receives the encrypted pack through the app update.

Use stable quest IDs in plaintext packs. Reusing an ID within a pack updates the quest on import while keeping local completions intact. A new global generation replaces older active bundled packs by archiving quests outside the current generation, without awarding XP.
