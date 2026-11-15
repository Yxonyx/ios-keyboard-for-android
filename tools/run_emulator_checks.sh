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
# sys.boot_completed can precede mounting emulated shared storage (notably on
# the 16 KB image). Wait for the actual directory operation, not just boot flags.
storage_ready=false
for attempt in {1..30}; do
  if adb shell mkdir -p /sdcard/Download/ime-compat >/dev/null 2>&1; then
    storage_ready=true
    break
  fi
  sleep 1
done
if [[ "$storage_ready" != true ]]; then
  echo "Emulator shared storage was not writable within 30 seconds." >&2
  exit 1
fi
result=0
./gradlew --no-daemon "$@" || result=$?
mkdir -p app/build/outputs/device-diagnostics
adb pull /sdcard/Download/ime-compat app/build/outputs/device-diagnostics/ >/dev/null 2>&1 || true
exit "$result"
