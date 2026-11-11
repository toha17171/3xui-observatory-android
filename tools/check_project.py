#!/usr/bin/env python3
"""Offline sanity checks. A full Android build still requires Android SDK/Gradle."""
from pathlib import Path
from xml.etree import ElementTree
import re

root = Path(__file__).resolve().parents[1]
xml_files = list((root / 'app/src/main').rglob('*.xml'))
for filename in xml_files:
    ElementTree.parse(filename)

sources = list((root / 'app/src/main/java').rglob('*.java'))
assert len(sources) >= 6
java = '\n'.join(p.read_text() for p in sources)
xml = '\n'.join(p.read_text() for p in xml_files)
resources = set(re.findall(r'@\+id/([\w_]+)', xml))
ids = set(re.findall(r'R\.id\.([\w_]+)',java))
missing = sorted(ids - resources)
assert not missing, f'Unknown view IDs: {missing}'
for name in ('xrayObservatory', 'Authorization', 'AndroidKeyStore', 'JobScheduler', 'APPWIDGET_UPDATE'):
    assert name in java or name in xml, f'Feature not present: {name}'
assert (root / '.github/workflows/android.yml').exists()
assert (root / 'app/build.gradle.kts').exists()
print(f'OK: {len(xml_files)} XML, {len(sources)} Java sources, {len(ids)} view IDs')
