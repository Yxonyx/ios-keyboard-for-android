#!/usr/bin/env python3
"""Verify vendored engine/data bytes against the recorded source inventory."""
from pathlib import Path
import hashlib,json
root=Path(__file__).resolve().parents[1]
base=root/'ui-demo/engine/src/main/cpp/upstream'
m=json.loads((base/'SOURCE.json').read_text())
actual={str(p.relative_to(base)) for p in base.rglob('*') if p.is_file() and p.name!='SOURCE.json'}
assert actual==set(m['files']), 'Upstream inventory differs'
for name,sha in m['files'].items():
 assert hashlib.sha256((base/name).read_bytes()).hexdigest()==sha, name
base=root/'ui-demo/engine/src/main/assets/dictionaries'
for name,entry in json.loads((base/'SOURCE.json').read_text())['files'].items():
 assert hashlib.sha256((base/name).read_bytes()).hexdigest()==entry['sha256'], name
manifest=(root/'ui-demo/app/src/main/AndroidManifest.xml').read_text()
assert 'android.permission.INTERNET' not in manifest, 'Unexpected network permission'
# RECORD_AUDIO is allowed since 0.7: dictation goes through Google's recognizer (owner's decision,
# 2026-11-11). The app itself still never gets network access.
print('Verified pinned native source, dictionaries and the no-network manifest constraint.')
