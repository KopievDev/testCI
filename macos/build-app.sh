#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
rm -rf build BridgeNotes.app BridgeNotes-macOS.zip
swift build -c release
BIN="$(swift build -c release --show-bin-path)/BridgeNotesMac"
APP="BridgeNotes.app"
mkdir -p "$APP/Contents/MacOS" "$APP/Contents/Resources"
cp "$BIN" "$APP/Contents/MacOS/BridgeNotesMac"
cp Info.plist "$APP/Contents/Info.plist"
chmod +x "$APP/Contents/MacOS/BridgeNotesMac"
codesign --force --deep --sign - "$APP"
ditto -c -k --sequesterRsrc --keepParent "$APP" BridgeNotes-macOS.zip
printf 'Built %s\n' "$PWD/BridgeNotes-macOS.zip"
