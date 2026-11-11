#!/usr/bin/env python3
"""Rebuild the full emoji catalog from the versioned local Unicode / CLDR sources."""
from pathlib import Path
import json,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]
data=root/'data/unicode';langs=['hu','en','de','fr','es','it','pt','pl','nl'];annotations={}
for lang in langs:
 for file in [lang+'.xml',lang+'-derived.xml']:
  for node in ET.parse(data/file).iter('annotation'):
   annotations.setdefault(lang,{}).setdefault(node.attrib.get('cp','').replace('️',''),[]).extend((node.text or '').split(' | '))
def keywords(words,known):
 # Search is substring based, so a word contained in another kept word adds nothing.
 words=list(dict.fromkeys(w.strip().lower() for w in words if w.strip()))
 return ' | '.join(w for w in words if not any(w in o for o in known) and not any(w!=o and w in o for o in words))
text=(data/'emoji-test.txt').read_text();version=re.search(r'# Version: ([\d.]+)',text).group(1)
groups={'Smileys & Emotion':1,'People & Body':1,'Animals & Nature':2,'Food & Drink':3,'Activities':4,'Travel & Places':5,'Objects':6,'Symbols':7,'Flags':8,'Component':9}
entries=[];group='';subgroup='';coverage=dict.fromkeys(langs,0)
for line in text.splitlines():
 if line.startswith('# group:'):group=line[8:].strip()
 elif line.startswith('# subgroup:'):subgroup=line[11:].strip()
 elif '; fully-qualified' in line or '; component' in line:
  code,status=line.split(';',1);glyph=''.join(chr(int(x,16)) for x in code.split());match=re.search(r'# .*? E([\d.]+) (.+)$',status)
  name=match.group(2);cp=glyph.replace('️','');kw={}
  en=keywords(annotations['en'].get(cp,[]),[name.lower()])
  for lang in langs:
   words=annotations[lang].get(cp)
   if words:coverage[lang]+=1
   value=en if lang=='en' else keywords(words or [],[name.lower()]+en.split(' | '))
   if value:kw[lang]=value
  entries.append({'emoji':glyph,'name':name,'kw':kw,'group':groups[group],'subgroup':subgroup,'version':match.group(1)})
assert len(entries)==len({e['emoji'] for e in entries}), 'Duplicate Unicode sequences'
assert len(entries)==json.loads((data/'SOURCE.json').read_text())['entries'], 'Source/catalog count mismatch'
assert coverage['hu']==len(entries), 'Missing Hungarian annotations'
assert all(n>=0.95*len(entries) for n in coverage.values()), f'Low annotation coverage {coverage}'
asset=root/'ui-demo/app/src/main/assets/emoji/catalog.json'
asset.write_text(json.dumps({'version':version,'languages':langs,'entries':entries},ensure_ascii=False,separators=(',',':')))
print('Unicode',version,':',len(entries),'unique sequences,',asset.stat().st_size,'bytes')
print('Annotation coverage:',', '.join(f'{l} {n}/{len(entries)}' for l,n in coverage.items()))
