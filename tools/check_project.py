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
# All widget sizes must share a single layout. Switching RemoteViews layout IDs
# during resize caused blank or stale widgets on some Android launchers.
namespace='{http://schemas.android.com/apk/res/android}'
for provider in ('observatory_widget.xml', 'observatory_widget_large.xml'):
    path=root/'app/src/main/res/xml'/provider
    node=ElementTree.parse(path).getroot()
    assert node.attrib[namespace+'initialLayout']=='@layout/widget_dashboard', provider
widget=(root/'app/src/main/java/dev/observatory/xui/WidgetRenderer.java').read_text()
assert 'R.layout.widget_dashboard' in widget
assert 'R.layout.widget_expanded' not in widget and 'R.layout.widget_compact' not in widget
for i in range(1,9):
    assert f'w_node_row{i}' in resources
    assert f'R.id.w_node_name{i}' in widget
    assert f'R.id.w_node_delay{i}' in widget
print(f'OK: {len(xml_files)} XML, {len(sources)} Java sources, {len(ids)} view IDs; responsive widget checks')
