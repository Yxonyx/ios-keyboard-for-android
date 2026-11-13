# Build and validate

## Toolchain

- JDK 21.
- Android SDK platform 36 and build-tools 36.0.0.
- Android NDK **27.2.12479018** (r27c).
- Python 3 for source/data verification and generation.
- The checked-in Gradle 8.14.6 wrapper; Android Gradle Plugin 8.13.2.

Install the SDK tools using Android Studio or Android's command-line tools:

```sh
sdkmanager 'platforms;android-36' 'build-tools;36.0.0' 'ndk;27.2.12479018'
```

Set `ANDROID_HOME` to the SDK directory, or put `sdk.dir=/absolute/path/to/sdk` in
the untracked `ui-demo/local.properties`. When the NDK is outside the SDK, pass
`-PlatinimeNdkPath=/absolute/path/to/android-ndk-r27c` to each Gradle invocation.
Do not commit machine-specific paths. The first build needs network access to
download build dependencies; later builds may use `--offline` with a populated
Gradle cache. Offline typing is separate from build-time dependency downloads.

## Source and generated-data checks

```sh
python3 tools/verify_engine_sources.py
python3 tools/verify_public_release.py
python3 tools/build_layout.py
python3 tools/build_emoji_catalog.py
git diff --exit-code -- ui-demo/app/src/main/assets/geometry.json ui-demo/app/src/main/assets/emoji/catalog.json
```

All generated-data inputs are included. Neither screenshots nor external font
downloads are needed. Corresponding dictionary wordlists are in `data/dictionaries`;
the APK uses precompiled binary dictionaries verified against their source inventory.

## Compile and test

```sh
cd ui-demo
./gradlew --no-daemon :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebugAndroidTest :engine:assembleDebugAndroidTest
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`, with application ID
`hu.keyboard.proof.debug`. It is debug-signed. Release output is unsigned and has
application ID `hu.keyboard.proof`; there is no public production signing key or
update channel. Keep any future signing keys outside the repository.

## Device verification

Use a test device running Android 13/API 33 or later and select its serial explicitly:

```sh
adb -s DEVICE_SERIAL install -r app/build/outputs/apk/debug/app-debug.apk
adb -s DEVICE_SERIAL shell am start -W -n hu.keyboard.proof.debug/hu.keyboard.proof.SettingsActivity
```

Enable the keyboard using Android's input-method settings. The included TypingActivity
has synthetic message, search, numeric and password fields. Never enter real secrets
when gathering screenshots or logs.

```sh
./gradlew --no-daemon :engine:connectedDebugAndroidTest :app:connectedDebugAndroidTest
```

For an x86_64 emulator, add `-PengineAbis=x86_64` to both build and test commands.
The default APK includes ARM64 and 32-bit ARM. Use a dedicated emulator for automated tests; do not switch
someone's everyday keyboard during unattended testing.

## Packaged ABI and page-size checks

```sh
python3 tools/verify_apk_compatibility.py ui-demo/app/build/outputs/apk/debug/app-debug.apk
python3 tools/verify_apk_compatibility.py ui-demo/app/build/outputs/apk/release/app-release-unsigned.apk
```

These commands run from the repository root and inspect the built ELF files and
ZIP alignment. For an x86_64-only APK, append `--abis x86_64`. See
[the compatibility matrix](COMPATIBILITY.md) for the opt-in IME/editor tests and
physical-device validation gaps.

## Interface languages and typing languages

Settings → Languages and layout exposes nine typing languages, each with its own
supported layouts. The globe-key list must contain at least one language; removing
the active language selects the first remaining one. Choosing another active
language includes it in the cycle. Layout choices are remembered per language.

The independent interface selector uses Android 13+ application locales, including
“Follow system”. It changes settings, privacy/consent, editor actions and emoji
panel labels; it does not change the chosen typing dictionary or speech locale.
Android Settings also exposes the same nine application languages. Portuguese is
Brazilian Portuguese. Emoji keywords follow the typing language.

```sh
python3 tools/verify_translations.py
```

This verifies complete resource coverage and format arguments in English,
Hungarian, German, French, Spanish, Italian, Brazilian Portuguese, Polish and Dutch.
Bundled third-party legal texts retain their original language. Native-speaker
review is welcome; translation completeness is not an independent editorial review.
