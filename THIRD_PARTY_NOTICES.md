# Third-party notices

The default license for original project code and documentation is GPL-3.0-only
([LICENSE](LICENSE)). Existing explicit Apache-2.0 headers remain in effect for
those files. Third-party components retain their own licenses and copyright
notices; the default project license does not replace them.

| Component | Source and version | License | Local notice/source |
| --- | --- | --- | --- |
| AOSP LatinIME native files | [HeliBoard snapshot](https://github.com/HeliBorg/HeliBoard/tree/3e91b4f1bf711a8bd3eb2d049c8b285fcc9cfa10/app/src/main/jni) | Apache-2.0, original file notices preserved | `ui-demo/engine/src/main/cpp/upstream/LICENSE` and `SOURCE.json` |
| Nine dictionaries and corresponding compressed wordlists | [OpenBoard 1.4.5](https://github.com/openboard-team/openboard/tree/6c7582aae8577f2953a597a547924bbea3d832f4) | GPL-3.0, as declared by that repository | `data/dictionaries/LICENSE`; binary/source SHA-256 mapping in `ui-demo/engine/src/main/assets/dictionaries/SOURCE.json` |
| Inter variable font | [Google Fonts Inter](https://github.com/google/fonts/tree/main/ofl/inter), blob `047c92f6e2212473dc436020afed689527076d44` | SIL OFL 1.1; Copyright 2020 The Inter Project Authors | `ui-demo/app/src/main/assets/fonts/OFL.txt` and `SOURCE.json` |
| Unicode emoji data and CLDR annotations | Emoji 18.0; CLDR commit `ba1b0a5a42af337b2ae879abe8971333b06c1f91` | Unicode License V3 | `data/unicode/LICENSE.txt` and `SOURCE.json`; generated catalog under `assets/emoji/` |
| Gradle wrapper | Gradle 8.14.6 | Apache-2.0 | `LICENSES/Apache-2.0.txt`; distribution hash pinned in wrapper properties |

Android Gradle Plugin 8.13.2, AndroidX Test and JUnit are fetched as build/test
dependencies. See Gradle files for exact versions. The Android SDK and NDK are
external development tools and are not redistributed in this repository.

The original application layout explored an iPhone-like keyboard. This public
source snapshot excludes the private screenshot gallery, measurements, extracted
glyph paths and old screenshot-based evidence. `tools/build_layout.py` generates
all key rectangles without reading a reference image. Inter renders every key
label; the application draws its icon shapes directly. No Apple emoji font or
gesture-typing binary is included. The optional user font importer does not give
redistribution rights to fonts selected by a user.

Google's recognizer is an optional external service accessed using Android's
speech APIs. Its implementation and models are not part of this source release.

The same license texts and an attribution notice are bundled in the APK under
`assets/licenses/`, accessible from Settings → Karbantartás → Nyílt forráskódú licencek.

The two README screenshots in `docs/images/` were provided and approved for inclusion
by the project owner on 2026-11-11. They show the keyboard inside Grok; no ownership
or GPL relicensing of Grok's interface is asserted. They document the private preview
before the public renderer changes. They are documentation images, not app assets.
