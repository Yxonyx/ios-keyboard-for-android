# Working on this keyboard

- Read README.md and docs/BUILD.md. Preserve the GPL license, component notices,
  source inventories and the keyboard's no-INTERNET permission boundary.
- Keep private text, clipboard contents, signing keys, device captures containing
  conversations and machine-specific paths out of Git. Use synthetic input.
- Reproduce an editor or gesture failure before fixing it. Run unit/lint/build
  checks, plus IME instrumentation for input behavior. Use a disposable emulator:
  tools/run_emulator_checks.sh refuses physical devices for automated IME changes.
- Keep all nine interface translations and format arguments in sync. Typing
  language, interface language and layout are independent settings.
- Hold-space changes language. Emoji swipes change pages; long press opens variants.
  Check gestures do not accidentally insert emoji or spaces.
- Run tools/verify_engine_sources.py, tools/verify_public_release.py and
  tools/verify_translations.py. Generated assets must reproduce from checked-in data.
- Do not claim all-device compatibility, offline external speech, complete glyph
  rendering, a signed release or reproducible APKs without corresponding evidence.
- Describe candidate changes in CHANGELOG.md and validation in docs/VALIDATION.md.
  Keep PRs focused and never commit unrelated local changes or production keys.
