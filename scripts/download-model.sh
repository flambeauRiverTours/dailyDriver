#!/usr/bin/env sh
#
# Downloads a gated model artifact from Hugging Face (T030, T190).
#
# Every `.task` under `litert-community` is gated: an unauthenticated request returns HTTP 401
# with a short text body, *saved under the filename you asked for*. That is the trap this script
# exists to close. A failed download looks exactly like a successful one in `ls` until you notice
# it is 140 bytes instead of 300 MB, and the first time it happened the error page sat on disk
# named `gemma3-270m-it-q4.task` for a while before anyone checked.
#
# It also reports the container format, because that is not guessable from the extension. T031
# found MediaPipe dispatches its loader on the extension: `.task` takes the zip-bundle path and
# anything else takes the flat-flatbuffer path. The `-web` variants are flat despite being named
# `.task`, so they must be stored as `.bin` or they are rejected with "Unable to open zip archive".
#
# Usage:
#   HF_TOKEN=hf_xxx ./scripts/download-model.sh
#   ./scripts/download-model.sh gemma3-270m-it-q8.task           # a different file
#   ./scripts/download-model.sh gemma3-270m-it-q8.task /tmp/out  # a different directory
#
# The token is read from $HF_TOKEN, or from the file `huggingface-cli login` writes. It is never
# echoed, never written anywhere, and never passed as a URL parameter.

set -eu

REPO="${HF_REPO:-litert-community/gemma-3-270m-it}"
FILE="${1:-gemma3-270m-it-q8.task}"
OUT_DIR="${2:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"
OUT="$OUT_DIR/$FILE"

# `*.task`, `*.bin` and `*.litertlm` are gitignored, so the repo root is a safe landing spot for
# a licensed multi-hundred-megabyte artifact. Check rather than assume, because the cost of being
# wrong is committing Gemma weights to history.
if command -v git >/dev/null 2>&1 && git -C "$OUT_DIR" rev-parse --git-dir >/dev/null 2>&1; then
  if ! git -C "$OUT_DIR" check-ignore -q "$OUT" 2>/dev/null; then
    echo "REFUSING: $OUT is not gitignored." >&2
    echo "Model weights are Gemma-licensed and must never enter git history." >&2
    exit 1
  fi
fi

if [ -n "${HF_TOKEN:-}" ]; then
  TOKEN="$HF_TOKEN"
elif [ -r "$HOME/.cache/huggingface/token" ]; then
  TOKEN="$(cat "$HOME/.cache/huggingface/token")"
elif [ -r "$HOME/.huggingface/token" ]; then
  TOKEN="$(cat "$HOME/.huggingface/token")"
else
  echo "No Hugging Face token found." >&2
  echo >&2
  echo "  1. Accept the licence at https://huggingface.co/$REPO" >&2
  echo "  2. Create a read token at https://huggingface.co/settings/tokens" >&2
  echo "  3. Re-run as:  HF_TOKEN=hf_xxx $0" >&2
  echo >&2
  echo "Do not paste the token into any file in this repository." >&2
  exit 1
fi

URL="https://huggingface.co/$REPO/resolve/main/$FILE"
echo "Downloading $REPO / $FILE"

# --fail turns the 401 into a non-zero exit instead of a saved error page, which is the whole
# point. Belt and braces: the size and magic checks below run anyway, because --fail has been
# known not to catch a 200 response carrying an error body.
if ! curl -fL --progress-bar -H "Authorization: Bearer $TOKEN" -o "$OUT" "$URL"; then
  echo >&2
  echo "Download failed. A 401 here means the licence has not been accepted for $REPO," >&2
  echo "which is separate from having a valid token. Accept it in a browser, then retry." >&2
  rm -f "$OUT"
  exit 1
fi

SIZE=$(wc -c < "$OUT" | tr -d ' ')
if [ "$SIZE" -lt 1000000 ]; then
  echo >&2
  echo "REJECTED: $OUT is only $SIZE bytes, so this is an error page and not a model." >&2
  head -c 200 "$OUT" >&2
  echo >&2
  rm -f "$OUT"
  exit 1
fi

# Classify by looking for the zip signature at a known offset rather than anywhere in the first
# few bytes. The real bundles carry a 4-byte prefix before `PK`, so a loose search
# happens to give the right answer and would also match a flat file containing P and K by
# coincidence. Checked with `cmp` at both offsets instead.
is_zip=no
for off in 0 4; do
  if printf 'PK' | cmp -s -i "0:$off" -n 4 - "$OUT"; then is_zip=yes; fi
done
is_flat=no
printf 'TFL3' | cmp -s -i "0:4" -n 4 - "$OUT" && is_flat=yes
echo
echo "Saved  : $OUT"
echo "Size   : $SIZE bytes"
if [ "$is_zip" = yes ]; then
  echo "Format : zip task bundle. Keep the .task name; MediaPipe needs it to pick the bundle loader."
  command -v unzip >/dev/null 2>&1 && unzip -l "$OUT" | sed -n '4,8p'
elif [ "$is_flat" = yes ]; then
  echo "Format : flat TFLite flatbuffer. RENAME IT OFF .task before pushing to the device,"
  echo "         or MediaPipe rejects it with 'Unable to open zip archive' (T031)."
else
  echo "Format : unrecognised. Inspect before trusting it:"
  head -c 16 "$OUT" | od -An -c
fi
