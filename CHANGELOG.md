# Changelog

## Unreleased

- Swipe down on the keyboard (letters or suggestion bar) to hide it, as dragging the page
  does on iOS. Since 0.8.3 hides the Android 16 system bar that held the hide arrow, this
  is the quick way to read with the keyboard out of the way. A drag of more than about
  one and a half key rows is needed, so sliding to the key below still types it; accent
  popups and long presses are unaffected.

## 0.8.3-preview — 2026-11-19

- Decide between an emoji tap and a hold (keyboard picker) from the touch's own
  timestamps at release, so a busy device that delivers the release late can no longer
  open the picker from a tap.
- Autocorrect words typed without their accents (koszonom → köszönöm, kesobb → később,
  manana → mañana); before, only one changed letter was allowed, so two missing accents
  were offered in the bar but never applied. A bare form that the dictionary also lists
  is corrected only when its accented form is clearly preferred (szivesen, perche, wiec,
  amanha, jo); words like schon, esta, el, tu, la and ou are kept.
- Apply the dictionary's apostrophe corrections (im → I'm, cant → can't, ill → I'll,
  lets → let's); its other whitelist entries that would rewrite real words (hid → his)
  stay suggestions.
- Add a nine-language correction check on the emulator (100 typed words).
- Offer the lowercase form of a suggestion mid-sentence when the dictionary also lists a
  capitalised sentence-start form (köszönöm instead of Köszönöm); names stay capitalised.
- Quote the typed word in the suggestion bar with the typing language's own marks
  (“word” in English, „szó” in Hungarian, „Wort“ in German, « mot » in French and so on);
  it used Hungarian quotes in every language.
- Leave an empty suggestion cell without a divider, as on iOS; dividers only separate words.
- Show the dictation approval in the phone's own dialog style (it used a legacy
  look) with shorter wording and plain Allow / Not now buttons, in all nine languages.
- Name the debug build "iOS Keyboard (debug)" so system keyboard lists, which merge
  same-named keyboards on some phones, show it apart from the release build.
- Keep the microphone key neutral while dictating, as iOS does: a key-coloured disc
  and a filled microphone glyph replace the red circle.
- Redraw the emoji key's grinning face with iOS-like proportions (firmer outline,
  rounder eyes, half-moon mouth with a filled lower part), still drawn in code.
- Show the new typing language's name inside the space bar after a hold-space switch,
  fading out, instead of an Android toast. TalkBack announces it.
- Draw Search, Go, Send and Done return keys blue with a white label (Next and plain
  return keep the ordinary key), matching iOS.
- On Android 16, hide the system IME back/switch buttons that were drawn over the
  emoji and microphone keys; tapping the lower half of the microphone could switch
  to another keyboard. Holding the emoji key now opens the keyboard picker, like
  holding the iOS globe key. Emoji opens on release instead of on touch.
- Correct Hungarian interface wording that described the visual preview as an
  unfinished "UI test" and dictation there as "not active yet".

- Improve the repository introduction, installation heading and FAQ for people
  looking for an iPhone-style Android keyboard; add relevant GitHub topics and
  a direct release-page link to the repository metadata. No app behavior changes.

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
