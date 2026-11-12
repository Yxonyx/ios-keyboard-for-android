#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-only
# Run only on an explicitly identifiable emulator, preserving synthetic UI diagnostics.
set -euo pipefail
project_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_dir/ui-demo"

test_serial="${ANDROID_SERIAL:-$(adb get-serialno)}"
if [[ "$test_serial" != emulator-* ]]; then
  echo "Refusing IME diagnostics on a physical device." >&2
  exit 1
fi
export ANDROID_SERIAL="$test_serial"
adb shell mkdir -p /sdcard/Download/ime-compat
result=0
./gradlew --no-daemon "$@" || result=$?
mkdir -p app/build/outputs/device-diagnostics
adb pull /sdcard/Download/ime-compat app/build/outputs/device-diagnostics/ >/dev/null 2>&1 || true
exit "$result"
