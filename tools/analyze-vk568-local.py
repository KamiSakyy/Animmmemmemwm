import json,hashlib,re,signal,zipfile,logging
from pathlib import Path
from loguru import logger
logger.remove()
logging.disable(logging.CRITICAL)
from androguard.misc import AnalyzeAPK
from androguard.core.axml import AXMLPrinter
from lxml import etree
root=Path(__file__).resolve().parents[1]
work=root/'workspace-output/vk568-local/build'
work.mkdir(parents=True,exist_ok=True)
a,vms,analysis=AnalyzeAPK(str(root/'org.xjiop.vkvideoapp-568.apk'))
report={'engine':'Androguard DAD','apk_sha256':hashlib.sha256((root/'org.xjiop.vkvideoapp-568.apk').read_bytes()).hexdigest(),'package':a.get_package(),'version_name':a.get_androidversion_name(),'version_code':a.get_androidversion_code(),'target_sdk':a.get_target_sdk_version(),'dex_count':len(vms),'classes':0,'methods':0,'java_ok':0,'java_failed':[],'critical':[],'full_disassembly':False}
(work/'AndroidManifest.xml').write_bytes(etree.tostring(a.get_android_manifest_xml(),pretty_print=True))
report['activities']=a.get_activities();report['services']=a.get_services();report['permissions']=a.get_permissions()
patterns={'hls':r'EXT-X|m3u8|M3U8|HlsDownloader','vk':r'vkvideo\.ru|video\.search|video_ext\.php','intercept':r'shouldInterceptRequest|onLoadResource','size':r'Content-Range|contentLength|Content-Length','tls':r'onReceivedSslError|SslErrorHandler','player':r'PlaybackService|DownloadsService'}
classes=[]
for vm in vms:
 for cls in vm.get_classes():
  name=cls.get_name();path=work/'dexasm'/(name.strip('L;')+'.dexasm');path.parent.mkdir(parents=True,exist_ok=True)
  lines=['class '+name,'super '+str(cls.get_superclassname())]
  for field in cls.get_fields():lines.append('field '+field.get_name()+' '+field.get_descriptor())
  for method in cls.get_methods():
   report['methods']+=1;lines.append('\nmethod '+method.get_name()+method.get_descriptor())
   offset=0
   for ins in method.get_instructions():
    lines.append(str(offset)+' '+ins.get_name()+' '+ins.get_output());offset+=ins.get_length()
  text='\n'.join(lines);path.write_text(text)
  tags=[k for k,v in patterns.items() if re.search(v,text)]
  if tags:report['critical'].append({'class':name,'tags':tags})
  classes.append((0 if name.startswith('Lorg/xjiop/') else 1 if 'vk' in tags or 'intercept' in tags or 'hls' in tags else 2,cls))
report['classes']=len(classes);report['full_disassembly']=True
(root/'handoff/VKVIDEO-568-local-progress.json').write_text(json.dumps(report,indent=2)+'\n')
def alarm(signum,frame):raise TimeoutError('Class decompilation deadline')
signal.signal(signal.SIGALRM,alarm)
for i,(_,cls) in enumerate(sorted(classes,key=lambda x:(x[0],x[1].get_name()))):
 try:
  signal.alarm(8)
  source=cls.get_source()
  if not source:raise ValueError('No source')
  signal.alarm(0)
  path=work/'java'/(cls.get_name().strip('L;')+'.java');path.parent.mkdir(parents=True,exist_ok=True);path.write_text(source)
  report['java_ok']+=1
 except Exception as e:
  signal.alarm(0);report['java_failed'].append({'class':cls.get_name(),'error':type(e).__name__})
 if i%200==0:(root/'handoff/VKVIDEO-568-local-progress.json').write_text(json.dumps(report,indent=2)+'\n')
report['finished']=True
(root/'handoff/VKVIDEO-568-local-progress.json').write_text(json.dumps(report,indent=2)+'\n')
