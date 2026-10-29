"""Validate bundled UI translations without Android or network access."""
from pathlib import Path
import json,re,xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1]/'app/src/main'
catalogues=root/'assets/i18n';english=json.loads((catalogues/'en.json').read_text());tags=json.loads((catalogues/'languages.json').read_text())
assert tags[0]=='en-GB' and len(tags)==len(set(tags))
placeholders=lambda text:sorted(re.findall(r'\{[0-9]+\}',text))
for tag in tags:
    assert re.fullmatch(r'[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*',tag),tag
    if tag=='en-GB':continue
    table=json.loads((catalogues/f'{tag}.json').read_text())
    for source,target in table.items():
        assert source in english,(tag,source)
        assert placeholders(source)==placeholders(target),(tag,source,'unsafe placeholders')
        assert target.strip(),(tag,source,'empty translation')
config=ET.parse(root/'res/xml/locales_config.xml').getroot();ns='{http://schemas.android.com/apk/res/android}'
assert {n.attrib[ns+'name'] for n in config}==set(tags)
print(f'{len(tags)} language choices; {len(english)} UI phrases; all placeholders and locale declarations valid.')
