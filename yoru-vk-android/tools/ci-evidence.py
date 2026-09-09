import json, os, urllib.request, urllib.parse, hashlib
import xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[2]
suites=[]
for p in (root/'yoru-vk-android/app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    e=ET.parse(p).getroot()
    suites.append({k:e.attrib.get(k) for k in ('name','tests','failures','errors','skipped')})
assert suites and all(int(s['failures'] or 0)==0 and int(s['errors'] or 0)==0 for s in suites)
manifest=(root/'vk-manifest.txt').read_text()
assert "package: name='app.yoru.vk.web'" in manifest
assert "versionName='0.2.1'" in manifest
assert 'application-debuggable' not in manifest
source=root/'yoru-vk-android/app/src/main/java/app/yoru/vk'
assert not (source/'TokenVault.java').exists() and not (source/'TokenParser.java').exists()
assert 'api.vk.com/method' not in (source/'Api.java').read_text()
assert 'addJavascriptInterface' not in (source/'WebActivity.java').read_text()
assert 'getCookie(' not in (source/'WebActivity.java').read_text()
result={'manual_token_required':False,'native_vk_api_calls':False,'workflow_run':os.environ.get('GITHUB_RUN_ID'),'commit':os.environ.get('GITHUB_SHA'),'tests':suites,'manifest':manifest,'certificate':(root/'vk-cert.txt').read_text(),'apk_sha256':hashlib.sha256((root/'apk-output/YORU-VK-0.2.1-prototype.apk').read_bytes()).hexdigest(),'vk_authenticated_playback_tested':False,'device_ui_tested':False}
query='{animes(ids:"11757",limit:1){id name russian english kind score status episodes episodesAired airedOn{year} poster{mainUrl originalUrl} genres{russian name} descriptionHtml}}'
try:
    req=urllib.request.Request('https://shikimori.io/api/graphql',data=json.dumps({'query':query}).encode(),headers={'Content-Type':'application/json','User-Agent':'YORU-VK/0.2.1 (Android; independent prototype)'})
    with urllib.request.urlopen(req,timeout=25) as r:
        data=json.load(r)
        rows=data.get('data',{}).get('animes',[])
        result['shikimori_public_probe']={'ok':bool(rows),'id':rows[0].get('id') if rows else None,'name':rows[0].get('name') if rows else None,'fields':list(rows[0].keys()) if rows else [],'errors':data.get('errors',[])}
except Exception as e:
    result['shikimori_public_probe']={'ok':False,'error_type':type(e).__name__}
(root/'handoff/YORU-VK-0.2.1-build.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
print(json.dumps(result,ensure_ascii=False,indent=2))
