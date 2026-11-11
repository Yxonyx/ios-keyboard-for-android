#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Fail when publication sources, licenses or generated artwork lose their provenance."""
from pathlib import Path
import gzip
import hashlib
import json
import re
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition, message):
    if not condition:
        raise SystemExit(message)


for excluded in ("references", "evidence", "tools/trace_ink.py", "tools/measure_reference.py"):
    require(not (ROOT / excluded).exists(), f"Private reference material present: {excluded}")

require((ROOT / "LICENSE").read_bytes() == (ROOT / "LICENSES/GPL-3.0-only.txt").read_bytes(),
        "Default license differs from the preserved GPL text")
for path in (ROOT / "LICENSES").glob("*.txt"):
    bundled = ROOT / "ui-demo/app/src/main/assets/licenses" / path.name
    require(bundled.is_file() and bundled.read_bytes() == path.read_bytes(),
            f"APK license missing or changed: {path.name}")
notice = (ROOT / "ui-demo/app/src/main/assets/licenses/NOTICE.txt").read_text()
require((ROOT / "THIRD_PARTY_NOTICES.md").read_text() in notice, "Bundled attribution is stale")

dictionaries = ROOT / "ui-demo/engine/src/main/assets/dictionaries"
inventory = json.loads((dictionaries / "SOURCE.json").read_text())
require(inventory["commit"] == "6c7582aae8577f2953a597a547924bbea3d832f4",
        "Dictionary upstream changed: repeat the licensing review")
require({p.name for p in dictionaries.glob("*.dict")} == set(inventory["files"]),
        "Dictionary inventory is incomplete")
for name, entry in inventory["files"].items():
    require(entry["license"] == "GPL-3.0-only", f"Unreviewed dictionary license: {name}")
    require(digest(dictionaries / name) == entry["sha256"], f"Dictionary hash mismatch: {name}")
    source = ROOT / entry["source"]["path"]
    require(digest(source) == entry["source"]["sha256"], f"Wordlist hash mismatch: {source.name}")
    require(gzip.decompress(source.read_bytes()).startswith(b"dictionary="),
            f"Missing editable wordlist: {source.name}")

unicode_dir = ROOT / "data/unicode"
inventory = json.loads((unicode_dir / "SOURCE.json").read_text())
require(digest(unicode_dir / "emoji-test.txt") == inventory["sha256"], "Emoji source hash mismatch")
for name, entry in inventory["cldr_sources"].items():
    require(digest(unicode_dir / name) == entry["sha256"], f"CLDR hash mismatch: {name}")
font_dir = ROOT / "ui-demo/app/src/main/assets/fonts"
font = json.loads((font_dir / "SOURCE.json").read_text())
require(digest(font_dir / "Inter.ttf") == font["sha256"], "Inter font hash mismatch")

geometry = json.loads((ROOT / "ui-demo/app/src/main/assets/geometry.json").read_text())
require(geometry["schemaVersion"] == 2 and geometry["generator"] == "tools/build_layout.py",
        "Keyboard layout must be generated from row rules")


def no_traces(value):
    if isinstance(value, dict):
        require(not {"ink", "paths", "icons"}.intersection(value), "Traced artwork in layout data")
        for child in value.values():
            no_traces(child)
    elif isinstance(value, list):
        for child in value:
            no_traces(child)


no_traces(geometry)
for state, keys in geometry["states"].items():
    for index, key in enumerate(keys):
        left, top, right, bottom = key["rect"]
        require(0 <= left < right <= geometry["sourceWidth"] and 1650 <= top < bottom <= 2556,
                f"Invalid {state} key: {key['label']}")
        for other in keys[index + 1:]:
            x0, y0, x1, y1 = other["rect"]
            require(min(right, x1) <= max(left, x0) or min(bottom, y1) <= max(top, y0),
                    f"Overlapping {state} keys: {key['label']} / {other['label']}")
renderer = (ROOT / "ui-demo/app/src/main/java/hu/keyboard/proof/KeyboardProofView.java").read_text()
require(not re.search(r"inkPath|drawMeasuredText|optJSONArray\(\"paths\"", renderer),
        "Screenshot-artwork renderer returned")
manifest = ET.parse(ROOT / "ui-demo/app/src/main/AndroidManifest.xml")
android = "{http://schemas.android.com/apk/res/android}"
permissions = {node.get(android + "name") for node in manifest.findall("uses-permission")}
require("android.permission.INTERNET" not in permissions, "Unexpected keyboard network permission")
require(manifest.find("application").get(android + "allowBackup") == "false", "Backup policy changed")
print("Verified public-source boundary, 9 dictionary wordlists, font/Unicode provenance, "
      "APK notices and generated key geometry.")
