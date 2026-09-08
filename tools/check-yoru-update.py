from pathlib import Path
import xml.etree.ElementTree as ET
import zipfile
import json
import sqlite3

root=Path(__file__).resolve().parents[1]
java=root/'yoru-android/app/src/main/java/app/yoru/mobile'
baseline=root/'handoff/YORU-4.17.2-stable-source.zip'
if baseline.exists():
    with zipfile.ZipFile(baseline) as z:
        for name in ['MediaCache.java','YoruCache.java']:
            assert (java/name).read_bytes()==z.read('yoru-android/app/src/main/java/app/yoru/mobile/'+name),name
        before=z.read('yoru-android/app/src/main/java/app/yoru/mobile/ImageLoader.java').decode()
        after=(java/'ImageLoader.java').read_text()
        assert after.replace('new ThreadPoolExecutor.AbortPolicy()','new ThreadPoolExecutor.CallerRunsPolicy()')==before
        print('Existing cache classes unchanged; ImageLoader only queue rejection policy changed')
for path in (root/'yoru-android/app/src').rglob('*.xml'):ET.parse(path)
for path in (root/'yoru-android/app/src/main/assets').rglob('*.json'):json.loads(path.read_text())
try:
    import javalang
except ImportError:
    print('Java syntax parse skipped: install javalang; Gradle tests/compile still required')
else:
    files=list((root/'yoru-android/app/src').rglob('*.java'))
    for path in files:javalang.parse.parse(path.read_text())
    print('Parsed Java files:',len(files))
profile=(java/'ProfileScreen.java').read_text()
assert 'Новые серии' not in profile
assert 'Только встроенный просмотр' not in profile
assert 'new WebView(' not in (java/'PlayerActivity.java').read_text()
assert 'CallerRunsPolicy' not in (java/'YoruApp.java').read_text()
assert 'removeAllViews()' not in (java/'CalendarScreen.java').read_text()
assert 'DiffUtil.calculateDiff' in (java/'CalendarScreen.java').read_text()
assert 'DiffUtil.calculateDiff' in (java/'DetailsActivity.java').read_text()
print('Static update checks: OK')
