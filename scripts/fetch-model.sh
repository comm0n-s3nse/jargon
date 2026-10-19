#!/usr/bin/env bash
# Downloads the built-in speech model (Whisper tiny.en, MIT, converted for whisper.cpp) into
# app/src/main/assets/models/ and checks it against a pinned SHA-256, so a fresh checkout builds
# an app that works. The model is not in git. Safe to run again: it does nothing if the right file
# is already there. Used by the README's build steps and by the F-Droid recipe.
set -euo pipefail
cd "$(dirname "$0")/.."

URL=https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin
SHA256=921e4cf8686fdd993dcd081a5da5b6c365bfde1162e72b08d75ac75289920b1f
DEST=app/src/main/assets/models/ggml-tiny.en.bin

sha256_of() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1
  else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

mkdir -p "$(dirname "$DEST")"
if [ -f "$DEST" ] && [ "$(sha256_of "$DEST")" = "$SHA256" ]; then
  echo "model already in place: $DEST"
  exit 0
fi
echo "downloading $URL"
curl -fL --retry 3 -o "$DEST.part" "$URL"
if [ "$(sha256_of "$DEST.part")" != "$SHA256" ]; then
  rm -f "$DEST.part"
  echo "checksum mismatch: not the model this app was built and tested with" >&2
  exit 1
fi
mv "$DEST.part" "$DEST"
echo "model ready: $DEST"
