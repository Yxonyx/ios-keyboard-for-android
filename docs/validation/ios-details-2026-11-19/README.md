# iOS detail pass, 2026-11-19

Before/after captures of this keyboard only, taken on the API 36 ARM64 emulator
(`medium_phone`, started with `-read-only`) in the bundled TypingActivity with empty
synthetic fields. "Before" is the public 0.8.2-preview APK; "After" is the debug build
of this change. No iOS screenshots or Apple artwork are included; the iOS Simulator
was used only as a visual reference outside Git.

- `dictation-active.png`: listening state, red disc replaced by a neutral key disc
  and filled microphone glyph.
- `emoji-glyph.png`: redrawn grinning-face glyph.
- `language-switch.png`: language name inside the space bar instead of a toast.
- `search-key.png`: blue primary return key for Search.
- `bottom-row.png`: Android 16's IME back/switch buttons no longer overlap the emoji
  and microphone keys.
