# Public source publication

This directory is the publication candidate. It is a source snapshot with a fresh
history, not a branch intended to be merged back over private development.

## Prepared

- GPL-3.0-only default license; component licenses and notices preserved.
- Dictionary binaries and editable wordlists pinned to the same OpenBoard commit.
- Unlicensed Portuguese patch excluded and replaced with the OpenBoard version.
- Reference screenshots, tracing scripts and extracted artwork excluded.
- Programmatic keyboard geometry and Inter labels; generated assets reproducible.
- Privacy, contribution, security, build and architecture documentation.
- Bundled license viewer, accessible offline from application settings.
- Separate debug application ID for side-by-side testing.
- CI checks for provenance, public-tree hygiene, generated assets, unit tests,
  lint and instrumentation.

## Publication and follow-up

1. Review the published snapshot and [validation evidence](VALIDATION.md).
2. The public repository is https://github.com/Yxonyx/ios-keyboard-for-android.
   It starts with a fresh history. The private development repository remains
   private because it contains historical screenshot assets and earlier releases.
3. GitHub private vulnerability reporting is enabled on the public repository.
4. Run the hosted workflow on the new remote and confirm both jobs finish. A
   checked-in workflow is not proof of a hosted pass.

Public source may be released as a development preview. Completing nine-language
UI integration, full accessibility, broader correction accuracy measurement,
offline speech, production signing and an update channel remain separate product
work. An unsigned release build is not a production APK.
