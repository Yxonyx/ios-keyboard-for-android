# Validation

## Compatibility and privacy update, 2026-11-12

The compatibility candidate passed 24 JVM unit tests, 11 app instrumentation tests
and 6 native instrumentation tests locally on the ARM64 API 36 emulator. No tests
were skipped. Clipboard tests verify that denied access never invokes the reader and that
revoked, expired, consumed or sensitive offers are discarded. A host-editor test
checks suppression in an IME_FLAG_NO_PERSONALIZED_LEARNING field with a positive
clipboard control in a normal field. The app tests include actual IME/editor interaction and declining
external speech approval without losing local typing. All nine language/layout
definitions rendered at four screen geometries in both themes; this exposed and
fixed the missing M key in AZERTY.

Debug/release builds and lint passed. The packaged APKs include ARM64 and 32-bit
ARM, with 16 KB ELF and uncompressed-ZIP alignment, no Internet permission, and
optional microphone/portrait hardware declarations. These packaging checks are
not a physical 32-bit ARM runtime result. See [COMPATIBILITY.md](COMPATIBILITY.md)
for the hosted matrix and the explicitly untested manufacturer/device cases.

### Hosted results and offline proof

All seven jobs passed for `a47977a640e255339240d0d8b5236ed8bf002fe0` in
[run 43295566207](https://github.com/Yxonyx/ios-keyboard-for-android/actions/runs/43295566207).
The tested source was merged through [PR #1](https://github.com/Yxonyx/ios-keyboard-for-android/pull/1).

| Configuration | Result |
| --- | --- |
| Build, lint, 24 JVM tests, provenance and APK inspection | Passed |
| Android 13 / API 33, x86_64 phone, 4 KB | 11 app + 6 native tests passed |
| Android 14 / API 34, x86_64 phone, 4 KB | 11 app + 6 native tests passed |
| Android 15 / API 35, x86_64 phone, 4 KB | 11 app + 6 native tests passed |
| Android 16 / API 36, x86_64 tablet, 4 KB | 11 app + 6 native tests passed |
| Android 15 / API 35, x86_64 phone, asserted 16 KB | 11 app + 6 native tests passed |
| Android TV 13 / API 33, asserted 32-bit x86 process | 6 native tests passed; no app UI certification |

The final ARM64 API 36 instrumentation run also passed all 17 tests with airplane
mode enabled, Wi-Fi and mobile data disabled, and `dumpsys connectivity` reporting
`Active default network: none` and `mDefaultNetwork=null` before execution.
This establishes offline operation of the tested typing/correction/editor paths;
external speech recognition was declined or blocked by the consent gate, not
validated as offline speech. The emulator's previous network settings were restored.
Synthetic editor screenshots and detailed local logs were retained separately.

## Initial source preview, 2026-11-11

These results apply to the public-source 0.8.0-preview candidate, including the
programmatic renderer, Inter labels, OpenBoard Portuguese dictionary and new name.

| Check | Result |
| --- | --- |
| Debug APK, unsigned release APK and both instrumentation APKs | Built successfully with JDK 21, SDK 36, NDK 27.2.12479018 |
| Debug and release Android lint | Completed successfully; existing warnings remain |
| Visual renderer smoke check | Opened the public debug build on the API 36 emulator; inspected light and dark keyboard previews and the full new title |
| Offline license screen | Opened from Settings → Karbantartás → Nyílt forráskódú licencek on the emulator; notice text and source links rendered |
| Hosted CI | Both jobs passed for `c915003c072055fa3c03285042ae4f305de8bb8a`: [run 43106862109](https://github.com/Yxonyx/ios-keyboard-for-android/actions/runs/43106862109), including API 35 x86_64 instrumentation |
| JVM unit tests | 19 passed, 0 failures |
| App integration on ARM64 API 36 emulator | 1 passed, 0 failures |
| Native engine on ARM64 API 36 emulator | 5 passed, 0 failures; includes opening all nine dictionaries and recognizing common words |
| Native-source and dictionary inventories | SHA-256 checks passed |
| Editable dictionary wordlists | All nine included and SHA-256 verified |
| Generated layout and emoji catalog | Byte-identical after regeneration |
| Public-source boundary and APK license checks | Passed `verify_public_release.py` |
| Secret scan | Gitleaks 8.30.1 reported no findings in the source snapshot; a separate scan of 18 private-history commits also reported no findings |
| Release permission/name inspection | `iOS Keyboard for Android`; `RECORD_AUDIO`; no `INTERNET` permission |

## Reproduce

Follow [BUILD.md](BUILD.md). Source verification is offline; build dependencies
must first be available in the Gradle cache. For an externally stored NDK, append
`-PlatinimeNdkPath=/absolute/path/to/android-ndk-r27c` to Gradle commands.

```sh
python3 tools/verify_engine_sources.py
python3 tools/verify_public_release.py
python3 tools/build_layout.py
python3 tools/build_emoji_catalog.py
git diff --exit-code -- ui-demo/app/src/main/assets/geometry.json ui-demo/app/src/main/assets/emoji/catalog.json
cd ui-demo
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest :engine:assembleDebugAndroidTest
ANDROID_SERIAL=EMULATOR_SERIAL ./gradlew --no-daemon :engine:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

## Limits

The Xiaomi API 37 test attempt was blocked by
`INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`. It is not counted as
a passing real-device run. The earlier private keyboard remains the selected IME
on that phone. The successful instrumentation results above are from an emulator.

Native compilation retains upstream Clang variable-length-array warnings; Gradle
reports deprecated features. Lint success does not mean zero warnings. Nine common
word checks establish load/lookup compatibility, not multilingual correction
accuracy. Dictation quality, accessibility, host-app coverage and production
signing need separate validation. No complete security or legal audit is claimed.

The README's two owner-approved screenshots show the private preview before the
public renderer changes. They are not new public-build device-validation evidence.
Hosted GitHub Actions results are separate from these local results; inspect the
repository's Actions page for the current run.
