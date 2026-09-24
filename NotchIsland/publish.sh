#!/usr/bin/env bash
# Builds both APKs, copies them into apks/ and regenerates update.json so the in-app
# updater picks the release up. Run from the repository root after bumping versionCode.
set -euo pipefail

cd "$(dirname "$0")"
: "${ANDROID_HOME:?set ANDROID_HOME to an SDK with platform 34 and build-tools 34.0.0}"

gradle --no-daemon testDebugUnitTest assembleDebug assembleRelease

cd ..
cp NotchIsland/app/build/outputs/apk/debug/app-debug.apk apks/NotchIsland-debug.apk
cp NotchIsland/app/build/outputs/apk/release/app-release.apk apks/NotchIsland-release.apk

version_code=$(grep -oP 'versionCode \K[0-9]+' NotchIsland/app/build.gradle)
version_name=$(grep -oP 'versionName "\K[^"]+' NotchIsland/app/build.gradle)

python3 - "$version_code" "$version_name" <<'PY'
import json, os, sys
code, name = int(sys.argv[1]), sys.argv[2]
manifest = json.load(open('update.json'))
manifest['versionCode'] = code
manifest['versionName'] = name
manifest['sizeBytes'] = os.path.getsize('apks/NotchIsland-release.apk')
manifest['debugSizeBytes'] = os.path.getsize('apks/NotchIsland-debug.apk')
json.dump(manifest, open('update.json', 'w'), indent=2, ensure_ascii=False)
open('update.json', 'a').write('\n')
print(f"update.json now advertises {name} ({code})")
PY

echo "Edit update.json's notes, then commit apks/ and update.json."
