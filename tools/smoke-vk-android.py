import subprocess,time,re,json,hashlib,xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
result={'steps':[],'physical_device':False,'authenticated_playback_tested':False}
last=''
def adb(*args,timeout=25):return subprocess.check_output(['adb',*args],timeout=timeout,stderr=subprocess.STDOUT).decode(errors='replace')
def dump():
 global last
 adb('shell','uiautomator','dump','/sdcard/vk-smoke.xml')
 last=adb('shell','cat','/sdcard/vk-smoke.xml')
 return list(ET.fromstring(last).iter('node'))
def wait(predicate,timeout=90):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  try:
   found=[n for n in dump() if predicate(n.attrib)]
   if found:return found[0]
  except Exception:pass
  time.sleep(2)
 raise RuntimeError('Expected UI element did not appear')
def text(s):return lambda a:s.casefold() in (a.get('text','')+' '+a.get('content-desc','')).casefold()
def tap(n):
 b=list(map(int,re.findall(r'\d+',n.attrib['bounds'])));adb('shell','input','tap',str((b[0]+b[2])//2),str((b[1]+b[3])//2))
try:
 apk=root/'apk-output/YORU-VK-0.2.1-prototype.apk'
 result['apk_sha256']=hashlib.sha256(apk.read_bytes()).hexdigest()
 adb('install','-r',str(apk),timeout=90)
 adb('shell','am','start','-W','-n','app.yoru.vk.web/app.yoru.vk.MainActivity')
 wait(lambda a:a.get('class')=='android.widget.LinearLayout' and a.get('clickable')=='true' and ' · ' in a.get('content-desc',''))
 result['steps'].append('Native Shikimori feed has actual cards')
 tap(wait(lambda a:a.get('clickable')=='true' and a.get('text','').casefold()=='мастера меча онлайн'))
 card=wait(lambda a:a.get('class')=='android.widget.LinearLayout' and a.get('content-desc','').casefold().startswith('мастера меча онлайн.'))
 result['steps'].append('Russian Shikimori search returns Sword Art Online card')
 tap(card);wait(text('Какую серию ищем?'));result['steps'].append('Native details and episode controls open')
 tap(wait(text('Смотреть · найти видео ВК')));tap(wait(lambda a:a.get('text')=='В приложении' and a.get('clickable')=='true'))
 wait(text('Поиск видео ВК'));result['steps'].append('Dedicated VK search screen opens')
 try:
  wait(text('На странице есть результаты ВК.'),timeout=70);result['vk_web_results_visible']=True
 except Exception:result['vk_web_results_visible']=False
 result['native_catalog_smoke_ok']=True
except Exception as e:
 result['native_catalog_smoke_ok']=False;result['error']=str(e)
finally:
 (root/'handoff/YORU-VK-0.2.1-android-smoke.json').write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n')
 (root/'vk-ui-last.xml').write_text(last)
 print(json.dumps(result,ensure_ascii=False,indent=2))
