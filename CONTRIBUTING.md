# Contributing

This is an early Android keyboard preview. Small changes with reproducible examples
are welcome. Use invented text and a dedicated test application/device profile.

1. Read [build instructions](docs/BUILD.md) and reproduce the behavior first.
2. Keep changes focused. Include validation for editor-state, native-engine or
   privacy-sensitive behavior. Document language-data provenance and licensing.
3. Run the provenance/public-source checks, unit tests, lint and an APK build.
   Changes to editor integration should also run instrumentation tests.
4. Explain the trigger, resulting behavior and validation in the pull request.

Never submit credentials, signing keys, private recordings, personal clipboard
data or screenshots containing other people's conversations. Do not add fonts,
images, wordlists or binary libraries without a traceable source and license.
Do not modify vendored native files casually; keep source inventories verifiable.

Contributions are submitted under the license applicable to the files being
changed: GPL-3.0-only by default, with explicit existing file licenses preserved.
No copyright assignment is required.
