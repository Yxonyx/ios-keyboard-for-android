#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Inspect the built APK, including every native library's ELF and ZIP alignment."""
import argparse
import json
import os
from pathlib import Path
import struct
import subprocess
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument("apk", type=Path)
parser.add_argument("--abis", default="arm64-v8a,armeabi-v7a")
parser.add_argument("--aapt", type=Path, help="Override the pinned SDK build-tools aapt binary")
args = parser.parse_args()
expected = set(args.abis.split(","))
report = {"apk": args.apk.name, "libraries": []}
if args.aapt is None:
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    local = Path(__file__).resolve().parents[1] / "ui-demo/local.properties"
    if not sdk and local.is_file():
        for line in local.read_text().splitlines():
            if line.startswith("sdk.dir="):
                sdk = line.split("=", 1)[1]
    if not sdk:
        parser.error("Set ANDROID_HOME or pass --aapt /path/to/build-tools/36.0.0/aapt")
    args.aapt = Path(sdk) / "build-tools/36.0.0" / ("aapt.exe" if os.name == "nt" else "aapt")
badging = subprocess.check_output([str(args.aapt), "dump", "badging", str(args.apk)], text=True)
permissions = subprocess.check_output([str(args.aapt), "dump", "permissions", str(args.apk)], text=True)
assert "android.permission.INTERNET" not in permissions, "Packaged APK has network access"
assert "sdkVersion:'33'" in badging, "Minimum Android version changed: review the test matrix"
for feature in ["android.hardware.microphone", "android.hardware.screen.portrait"]:
    assert f"uses-feature-not-required: name='{feature}'" in badging, f"Unnecessary device filter: {feature}"
report["minimum_api"] = 33
report["internet_permission"] = False
report["microphone_required"] = False
report["portrait_hardware_required"] = False
seen = set()
with zipfile.ZipFile(args.apk) as archive, args.apk.open("rb") as raw:
    for entry in archive.infolist():
        if not entry.filename.startswith("lib/") or not entry.filename.endswith(".so"):
            continue
        abi = entry.filename.split("/")[1]
        seen.add(abi)
        data = archive.read(entry)
        assert data[:4] == b"\x7fELF" and data[5] == 1, entry.filename
        bits = {1: 32, 2: 64}[data[4]]
        if bits == 64:
            offset = struct.unpack_from("<Q", data, 32)[0]
            stride, count = struct.unpack_from("<HH", data, 54)
            align_offset, align_format = 48, "<Q"
        else:
            offset = struct.unpack_from("<I", data, 28)[0]
            stride, count = struct.unpack_from("<HH", data, 42)
            align_offset, align_format = 28, "<I"
        alignments = []
        for index in range(count):
            header = offset + index * stride
            if struct.unpack_from("<I", data, header)[0] == 1:  # PT_LOAD
                alignments.append(struct.unpack_from(align_format, data, header + align_offset)[0])
        assert alignments and min(alignments) >= 16384, (entry.filename, alignments)
        raw.seek(entry.header_offset + 26)
        filename_length, extra_length = struct.unpack("<HH", raw.read(4))
        data_offset = entry.header_offset + 30 + filename_length + extra_length
        if entry.compress_type == zipfile.ZIP_STORED:
            assert data_offset % 16384 == 0, (entry.filename, "unaligned uncompressed library")
        report["libraries"].append({"path": entry.filename, "bits": bits,
                                    "elf_load_alignments": alignments,
                                    "zip_compressed": entry.compress_type != zipfile.ZIP_STORED})
assert seen == expected, ("Unexpected or missing native ABI", seen, expected)
print(json.dumps(report, indent=2))
