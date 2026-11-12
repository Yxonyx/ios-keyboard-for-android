# Android and device compatibility

Compatibility is established by tests on identified configurations, not by the
absence of a crash on one phone. The project does not currently claim to work on
most active devices, all Android releases, or every manufacturer's ROM.

## Installation boundary

- **Android 13 / API 33 is the minimum**, declared by both the app and native module.
  Android 12 and older are unsupported; changing only `minSdk` would be insufficient
  because the implementation uses newer framework and Java APIs.
- The default APK now builds **ARM64 and 32-bit ARM** native libraries. x86_64 and
  x86 builds are available for emulator testing through `-PengineAbis=...`.
  Building a 32-bit ARM library is not proof of running it on a physical 32-bit phone.
- The microphone is optional in the manifest. A device without a speech recognizer
  can still use ordinary typing; no Google service is needed for typing/correction.
- Vendor emoji fonts determine how newly added emoji appear. Having searchable
  metadata for an emoji does not ensure that an older device can display its glyph.
- No tablet, foldable, Chromebook or Android Go certification is claimed.

## Automated matrix

The GitHub workflow uses separate jobs and preserves each result independently.
The table below describes the configurations; inspect the linked workflow results
and the validation record for actual passes. A configured row is not a passing test.

| Android | API | Emulator and native process | Screen/profile | Page size |
| --- | --- | --- | --- | --- |
| 13 | 33 | x86_64 | Pixel 7 phone | 4 KB |
| 14 | 34 | x86_64 | Pixel 7 phone | 4 KB |
| 15 | 35 | x86_64 | Pixel 7 phone | 4 KB |
| 16 | 36 | x86_64 | Pixel C tablet | 4 KB |
| 15 | 35 | x86_64, official experimental 16 KB image | Pixel 7 phone | 16 KB, asserted before tests |
| Android TV 13 | 33 | 32-bit x86 native-engine fixture only | 1080p TV | 4 KB |

The 32-bit row exercises the native engine, not a telephone UI. The regular
Android 13 Google x86_64 image rejected a 32-bit APK with
`INSTALL_FAILED_NO_MATCHING_ABIS`; zero tests ran in that attempt. Android documents
[regular emulator images from API 31 onward as 64-bit only](https://developer.android.com/google/play/requirements/64-bit).
The SDK catalog still provides an actual x86 Android TV image, which is used as a
bounded 32-bit runtime fixture. Its result cannot be presented as 32-bit ARM phone
or Android TV application certification.

The instrumentation runner verifies the requested process bitness. The packaging
check verifies the actual APK's ABI inventory and every ELF load segment and
uncompressed native-library ZIP offset for 16 KB alignment. The decoder's dictionary
mapping uses the runtime page size rather than a fixed 4 KB assumption. See Android's
[16 KB compatibility guidance](https://developer.android.com/guide/practices/page-sizes).

The app tests exercise all nine language definitions/layouts in light/dark modes at
small-phone, high-density-phone, landscape and tablet dimensions. An emulator-only,
explicitly opted-in test selects the actual IME, types into a host activity, checks
deletion/selection replacement, numeric input, the search action, emoji insertion
and literal password input, and restores the original IME afterward. Privacy tests
check the installed permission boundary and rejection of speech without approval.
The native tests open all nine dictionaries and exercise correction and lifecycle.

These are synthetic fixtures in the included host activity. They do not establish
behavior in every browser, messenger, WebView or vendor text editor. The rendering
matrix is not a screenshot-based visual or accessibility audit.

## Physical device validation still needed

| Family | Status for the public compatibility candidate |
| --- | --- |
| Google Pixel / stock Android | Not yet tested on a physical device |
| Samsung / One UI | Not yet tested |
| Xiaomi / HyperOS | Earlier private build used on a Xiaomi; public build installation was denied by the device, so it is not counted as a pass |
| OnePlus / OxygenOS, Oppo / ColorOS, Vivo | Not yet tested |
| Devices without Google services | Ordinary typing is designed to work; physical validation pending; Google dictation is not assumed available |
| 32-bit ARM / low-memory Android Go | Native build checked; physical runtime validation pending |
| Foldables and tablets | Emulator/layout checks only; posture changes and physical testing pending |

For a physical test, record the exact phone model, Android/API version, ROM build,
ABI, page size, APK checksum and test commit. Test portrait/landscape, navigation
insets, IME enable/switch/restart, process recreation, dark/light themes, password
and no-learning editors, browser and messenger fields, clipboard controls, emoji,
voice denial/approval/revocation, and recognizer absence. Use invented text only.
The ROM's installation/keyboard warnings must be reviewed as their exact messages;
the app does not bypass them.

## Reproduce the interactive tests safely

Only opt in on a disposable emulator:

```sh
cd ui-demo
ANDROID_SERIAL=EMULATOR_SERIAL ./gradlew --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.allowImeSwitch=true \
  -Pandroid.testInstrumentationRunnerArguments.expectedProcess64=true \
  :engine:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

The IME switching tests additionally refuse physical-device hardware identifiers.
For an x86 or x86_64 emulator, also set `-PengineAbis=x86` or `x86_64` respectively.
Ordinary connected tests skip IME switching unless it is explicitly enabled.
All source and test results remain reviewable through the
[Actions page](https://github.com/Yxonyx/ios-keyboard-for-android/actions).
