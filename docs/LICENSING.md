# Licensing review, 2026-11-11

## Decision and scope

Original project material is published under **GPL-3.0-only**, except files that
already have an explicit different license. The complete GPL text is in the root
`LICENSE`. Apache-2.0 native sources, explicitly marked adapter/integration files,
Inter's OFL and Unicode's data license remain intact. See [notices](../THIRD_PARTY_NOTICES.md).

This records the evidence used for the source snapshot. A file hash proves identity,
not authorship or licensing by itself; licensing is taken from the identified
upstream distribution and its notices.

## Dictionaries

The previous inventory identified HeliBoard binaries but did not include their
corresponding wordlists. The public snapshot now pins
[OpenBoard 1.4.5](https://github.com/openboard-team/openboard/tree/6c7582aae8577f2953a597a547924bbea3d832f4)
and preserves its [GPL-3.0 license](https://github.com/openboard-team/openboard/blob/6c7582aae8577f2953a597a547924bbea3d832f4/LICENSE).

Eight binaries (hu, en, de, fr, es, it, pl, nl) were fetched from that commit and
compared byte-for-byte with the private snapshot: all eight were identical. Their
compressed editable wordlists from the same commit are included in
`data/dictionaries`, with source and binary SHA-256 values in the asset inventory.
No separate per-file license exception was found for those files in that upstream
distribution. They are carried under the upstream repository's GPL-3.0 terms.

The private Portuguese binary came through HeliBoard from a
[separately patched Portuguese dictionary](https://github.com/joaohcopetti/aosp-dict-pt-br-patched).
The inspected patch repository did not contain a license. That binary is excluded
from this public snapshot. It is replaced by OpenBoard's `main_pt_br.dict` and
`pt_BR_wordlist.combined.gz`, both at the pinned OpenBoard commit. This is a data
change; correction results can differ from the patched version.

The complete wordlists are supplied rather than only linking to their source.
For dictionary editing/compilation, upstream documents the
[AOSP dictionary tools](https://github.com/remi0s/aosp-dictionary-tools). The
application build does not execute or redistribute a downloaded compiler JAR.

## Native engine, font and Unicode

- The native inventory stays pinned to HeliBoard commit
  `3e91b4f1bf711a8bd3eb2d049c8b285fcc9cfa10`, preserving the AOSP file headers and
  Apache-2.0 license. `verify_engine_sources.py` checks the complete file inventory.
- The bundled Inter font's Git blob hash matches Google Fonts'
  `ofl/inter/Inter[opsz,wght].ttf`: `047c92f6e2212473dc436020afed689527076d44`.
  Its OFL and attribution are preserved. The font inventory records SHA-256 as well.
- Unicode/CLDR inputs and the generated emoji metadata retain Unicode License V3.
  Source hashes and the CLDR commit are recorded in `data/unicode/SOURCE.json`.
  Emoji metadata does not include vendor glyph artwork.

## Artwork and history

The public tree excludes the original iPhone reference images, extracted glyphs,
measurement files, tracing scripts and old screenshots. Keyboard rectangles are
regenerated from row rules. The renderer no longer parses traced paths or measured
glyph boxes. Every key label uses Inter, including uppercase and accent choices;
icon shapes use the code-drawn implementation.

Deleting an image in a later commit does not remove it from older history. The
private repository therefore must not simply be switched to public. Publish the
reviewed source snapshot in a fresh repository/history. Keep the private repository
and its historical releases private. This avoids rewriting the active development
history or disturbing its uncommitted multilingual work.
