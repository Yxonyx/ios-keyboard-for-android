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

All nine typing languages are exposed in settings and Android subtype metadata.
Each language remembers its own layout; the globe cycles through a nonempty user
selection. Interface text uses English default Android resources plus eight locale
packs. Android 13+ LocaleManager persists the independent interface language and
exposes it in system app-language settings; first-use typing follows the system
locale, independently from the interface override. Accuracy and harmful-correction
rates have not been established across all nine dictionaries.

Run `python3 tools/verify_translations.py` to check complete locale-key coverage,
format placeholders, declared app locales and input subtypes. Native-speaker
editorial review of the translations remains welcome.

Dictation uses an external Android speech recognizer and may upload audio. No
speech-recognition library or audio model is bundled. See [privacy](../PRIVACY.md).
