#!/usr/bin/env bash
# Exports the on-device command log (T014) as JSONL, for §8's hand-labelling step (T075).
#
# Usage:  scripts/export-command-log.sh [output.jsonl]
#
# ── Why this script exists rather than a documented adb one-liner ────────────────────────────────
#
# Room runs SQLite in WAL mode, so recent writes live in `dailydriver.db-wal`, NOT in
# `dailydriver.db`. On a freshly-used install the .db file is ~4KB and effectively empty while the
# -wal holds everything. `adb pull`ing just the database therefore yields
#
#     sqlite3.OperationalError: no such table: command_log
#
# ...which reads like the feature never worked, when in fact all the data is sitting in a file you
# did not copy. That failure was reproduced before this script was written; it is the entire
# reason the script copies all three files together.
#
# Requires: a debuggable (debug) build, since it reads app-private storage via `run-as`.
set -euo pipefail

PKG="com.example.dailydriver"
OUT="${1:-command-log.jsonl}"
ADB="${ADB:-adb}"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

if ! "$ADB" shell "run-as $PKG true" >/dev/null 2>&1; then
  echo "error: cannot run-as $PKG — is a debug build installed and a device attached?" >&2
  exit 1
fi

# All three, in this order. The -wal is where the data usually is; the -shm is the index into it.
for f in dailydriver.db dailydriver.db-wal dailydriver.db-shm; do
  "$ADB" exec-out run-as "$PKG" cat "databases/$f" > "$TMP/$f" 2>/dev/null || true
done

python - "$TMP/dailydriver.db" "$OUT" <<'PY'
import json, sqlite3, sys

db, out = sys.argv[1], sys.argv[2]
# Opening the .db with its -wal alongside lets SQLite replay the log, which is the whole point.
rows = sqlite3.connect(db).execute(
    "SELECT input, target, tier, count, firstSeen, lastSeen FROM command_log ORDER BY count DESC"
).fetchall()

with open(out, "w", encoding="utf-8") as f:
    for input_, target, tier, count, first, last in rows:
        f.write(json.dumps({
            "input": input_,
            # What the deterministic tiers resolved to, NOT a §4.3 intent label. The label space is
            # expected to change before the dataset is frozen, so labelling happens downstream.
            "resolution": target or None,
            "tier": tier,
            "count": count,
            "firstSeen": first,
            "lastSeen": last,
        }, ensure_ascii=False) + "\n")

unresolved = sum(1 for r in rows if r[2] == "T3")
print(f"{len(rows)} distinct inputs -> {out}")
print(f"  {len(rows) - unresolved} resolved (T0/T1), {unresolved} unresolved (T3, the 'unknown' examples)")
print(f"  {sum(r[3] for r in rows)} total submissions")
PY
