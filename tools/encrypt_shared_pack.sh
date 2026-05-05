#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${PARUCHAN_SHARED_PACK_ENV:-$repo_root/agent-skills/paruchan-shared-packs/.env}"

if [[ -f "$env_file" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a
fi

: "${PARUCHAN_SHARED_PACK_PASSWORD:?Set PARUCHAN_SHARED_PACK_PASSWORD in $env_file}"

build_dir="${PARUCHAN_SHARED_PACK_BUILD_DIR:-/tmp/paru_quests_encrypt_shared_pack}"
mkdir -p "$build_dir"

javac -d "$build_dir" "$repo_root/tools/EncryptSharedPack.java"
java -cp "$build_dir" EncryptSharedPack "$@"
