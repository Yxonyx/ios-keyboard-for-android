# Architecture and current boundaries

`ui-demo/app` is the native Android application and input method.
`KeyboardService` owns the editor connection, keyboard/emoji views, composition,
clipboard offer and speech-recognition lifecycle. `KeyboardProofView` draws the
keyboard with Canvas. `Languages` defines layouts, accents and dictation locales.
`KeyboardSettings` stores preferences; `SettingsActivity` exposes the current UI.

`CompositionSession` makes conservative correction decisions at word boundaries,
rejects stale results and supports immediate Backspace undo. `CorrectionManager`
owns native decoding on one background executor. It verifies dictionary hashes
before copying assets into private application storage. Decoder failures fall
back to literal typing. Private and sensitive editors bypass correction.

`ui-demo/engine` isolates the JNI adapter and read-only AOSP LatinIME decoder.
Vendored upstream files are byte-verified against `upstream/SOURCE.json`; adapter
changes belong outside that tree.

`tools/build_layout.py` generates row rectangles without reference images. All
labels use Inter and all UI icons are drawn in application code or original vector
resources. `tools/build_emoji_catalog.py` generates emoji metadata from the included
Unicode and CLDR sources. The device or a user-imported font supplies emoji glyphs.

The nine language definitions are ahead of the current settings UI: only Hungarian
and English are exposed in its picker and Android subtype metadata. Translation
and settings integration are unfinished. Accuracy and harmful-correction rates
have not been established across all nine dictionaries.

Dictation uses an external Android speech recognizer and may upload audio. No
speech-recognition library or audio model is bundled. See [privacy](../PRIVACY.md).
