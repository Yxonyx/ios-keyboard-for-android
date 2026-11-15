#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-only
"""Reject incomplete locale packs, broken formatting and mismatched Android language declarations."""
from pathlib import Path
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
res = root / 'ui-demo/app/src/main/res'
def strings(folder):
    items = ET.parse(res / folder / 'strings.xml').getroot().findall('string')
    names = [v.attrib['name'] for v in items]
    assert len(names) == len(set(names)), f'Duplicate string in {folder}'
    return {v.attrib['name']: v.text for v in items if v.get('translatable') != 'false'}
base = strings('values')
formats = lambda value: sorted(re.findall(r'%(?:\d+\$)?[ds]', value))
for code in ['hu', 'de', 'fr', 'es', 'it', 'pt-rBR', 'pl', 'nl']:
    translated = strings('values-' + code)
    assert set(translated) == set(base), (code, 'Missing/extra keys', set(base) ^ set(translated))
    for key, value in translated.items():
        assert value and value.strip('"').strip(), (code, key, 'Empty translation')
        assert formats(value) == formats(base[key]), (code, key, 'Broken format arguments')
ns = '{http://schemas.android.com/apk/res/android}'
locales = {v.get(ns + 'name') for v in ET.parse(res / 'xml/locales_config.xml').getroot()}
assert locales == {'en', 'hu', 'de', 'fr', 'es', 'it', 'pt-BR', 'pl', 'nl'}
subtypes = [v.get(ns + 'languageTag') for v in ET.parse(res / 'xml/input_method.xml').getroot()]
assert len(subtypes) == 9 and {tag.split('-')[0] for tag in subtypes} == {tag.split('-')[0] for tag in locales}
print(f'Complete: {len(base)} translated strings in 9 interface languages; 9 Android input subtypes')
