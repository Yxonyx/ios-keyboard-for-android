# Release and verification

## Current distribution status

Signed development-preview APKs are published on
[GitHub Releases](https://github.com/Yxonyx/ios-keyboard-for-android/releases).
They are prereleases, not stable releases. Source is identified by the release tag;
APK SHA-256 checksums accompany each artifact. Bit-for-bit reproducible signed APKs
are not currently claimed.

The dedicated release signing certificate SHA-256 is:

```text
98:EE:C0:60:2F:2B:80:A7:41:99:BC:AA:C3:A7:BF:33:FB:4B:A2:1A:23:62:B5:28:18:D6:5F:0B:9C:A4:D8:ED
```

This public fingerprint is safe to share. The private keystore and its credentials
are not part of the repository, source archive or release attachments. Existing
debug-signed builds have a different signing identity; they are not in-place update
targets for this release. Release-to-release updates must preserve this identity.

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
channel. The expected preview-release fingerprint is published above.

Reproducible APK equality must be tested separately. Do not claim bit-for-bit
reproducibility merely because generated dictionaries and layout assets reproduce.
