# Release and verification

## Current distribution status

This repository publishes source for a development preview. It does not currently
provide a stable signed APK, a published production signing-certificate fingerprint,
or a verified reproducible signed release. The Gradle release task produces an
unsigned APK. Debug builds use a development key and must not be advertised as
production releases.

## Candidate checks

1. Use a clean checkout and the pinned toolchain in [BUILD.md](BUILD.md).
2. Run source/license verification, translation verification and generated-data
   reproduction. Review added assets and dependencies for licensing and secrets.
3. Build debug and release APKs, run unit tests and both lint variants, then run
   `tools/verify_apk_compatibility.py` on both APKs.
4. Run the full emulator workflow for the candidate commit. Earlier green commits
   are useful history, not proof for a changed candidate.
5. Check the IME on a real device with synthetic text, including emoji paging and
   search, language switching, private editors and speech refusal. Record exact
   device/software/build details and remaining gaps in COMPATIBILITY.md.
6. Review the changelog, screenshots, known limitations and version. Commit the
   result; tag only the reviewed commit.

## Before publishing a signed APK

Choose a dedicated release key and keep it outside Git and ordinary build output.
Back it up securely: an update must retain the application ID and signing identity.
Configure signing without checking passwords or key material into source. Record
the APK checksum and signing-certificate SHA-256 in the release notes so users can
verify the artifact. Verify a clean install and an in-place update preserving user
settings on a disposable device. Never uninstall a user’s keyboard to bypass a
certificate mismatch.

A release should include its source tag, build instructions, APK SHA-256,
certificate SHA-256, supported Android/CPU targets, tested configurations, and
known issues. Add an update channel only after this identity is established.

## Inspect an APK

Use `apksigner` from the pinned Android build-tools installation:

```sh
apksigner verify --verbose --print-certs keyboard.apk
shasum -a 256 keyboard.apk
```

A successful signature check only establishes that the APK has a valid signature.
Compare its certificate fingerprint with one published through a trusted maintainer
channel. No production fingerprint is published yet for this project.

Reproducible APK equality must be tested separately. Do not claim bit-for-bit
reproducibility merely because generated dictionaries and layout assets reproduce.
