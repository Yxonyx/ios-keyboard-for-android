#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Generate keyboard rectangles from row rules; no reference images or traced glyphs."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
WIDTH, HEIGHT = 1179, 2556
MARGIN, GAP, KEY_HEIGHT, ROW_STEP = 18, 16, 132, 162
TOP = 1728
UNIT = (WIDTH - 2 * MARGIN - 9 * GAP) / 10


def key(label, left, top, width):
    return {"label": label, "rect": [round(left, 3), top, round(left + width, 3), top + KEY_HEIGHT]}


def row(labels, index, unit=UNIT):
    width = len(labels) * unit + (len(labels) - 1) * GAP
    left = (WIDTH - width) / 2
    return [key(label, left + i * (unit + GAP), TOP + index * ROW_STEP, unit)
            for i, label in enumerate(labels)]


def page(first, second, third, mode):
    result = row(list(first), 0) + row(list(second), 1)
    result += row(list(third), 2, UNIT if len(third) == 7 else (7 * UNIT + 2 * GAP) / 5)
    side = 134
    result += [key(mode, MARGIN, TOP + 2 * ROW_STEP, side),
               key("delete", WIDTH - MARGIN - side, TOP + 2 * ROW_STEP, side)]
    bottom = TOP + 3 * ROW_STEP
    side = 266
    result += [key("123" if mode == "shift" else "ABC", MARGIN, bottom, side),
               key("space", MARGIN + side + GAP, bottom, WIDTH - 2 * (MARGIN + side + GAP)),
               key("return", WIDTH - MARGIN - side, bottom, side)]
    return result


data = {
    "schemaVersion": 2,
    "generator": "tools/build_layout.py",
    "sourceWidth": WIDTH, "sourceHeight": HEIGHT, "panelTop": 1650,
    "states": {
        "letters": page("QWERTZUIOP", "ASDFGHJKL", "YXCVBNM", "shift"),
        "numbers": page("1234567890", "-/:;()€&@”", ".,?!’", "#+="),
        "symbols": page("[]{}#%^*+=", "_\\|~<>$£¥•", ".,?!’", "123"),
    },
}
target = ROOT / "ui-demo/app/src/main/assets/geometry.json"
target.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(f"Generated {sum(map(len, data['states'].values()))} keys from row rules.")
