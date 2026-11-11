# Validation, 2026-11-11

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
