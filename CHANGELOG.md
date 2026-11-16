# Changelog

## 0.8.2-preview — 2026-11-16

- Request one discreet native haptic tap when the recognizer is ready to listen,
  respecting keyboard and system haptic settings. Document device/provider limits.

- Respect the configured system speech recognizer before Google fallbacks. Guide
  Android 15+ users to voice-input setup when a downloaded fallback lacks a
  privileged system binding, instead of repeatedly starting a muted microphone.
- Report the actual recognizer package in settings and link to system voice input.
- Add an emulator-only synthetic speech engine to verify partial text appears in
  the host editor before finalization, without duplicate text or audio recording.
- Match Hungarian A/E/I/O accent choices and popup placement to the supplied
  reference, including Ó immediately to the right of O.

## 0.8.1-preview — 2026-11-16

- Publish a signed preview APK with checksum and certificate verification instructions.
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

This is a signed development preview, with the limitations listed in README.md.
