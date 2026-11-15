# Changelog

## Unreleased — development preview

- Wait for writable emulator storage before starting diagnostics, including on the
  Android 15 16 KB image, whose boot flag can precede storage readiness.

- Fix emoji-grid gestures so horizontal swipes page in both directions without
  selecting a crossed cell. Add an IME gesture regression test.
- Show matching emoji while typing a search; page result batches and support the
  dark theme. Add search-and-insert coverage.
- Start the heart category with hearts; keep component-only characters out of the
  standalone grid. Verify catalog reachability through variant selection.
- Remove the globe control. Keep language switching on a long press of the space
  bar, with matching instructions in all nine interface languages.
- Replace the launcher artwork with an original keyboard mark and a monochrome
  variant for Android themed icons.
- Document font-dependent emoji limitations and the remaining release-signing,
  update-channel and physical-device validation work.

This entry describes the current source candidate, not a published stable APK.
