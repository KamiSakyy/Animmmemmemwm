import hashlib,json,re,zipfile,os,urllib.request,xml.etree.ElementTree as ET
from pathlib import Path
root=Path(__file__).resolve().parents[1]
work=root/'workspace-output/vk568/build'
report={'apk_sha256':hashlib.sha256((root/'org.xjiop.vkvideoapp-568.apk').read_bytes()).hexdigest(),'jadx_exit':os.environ.get('JADX_EXIT'),'apktool_exit':os.environ.get('APKTOOL_EXIT'),'tools':{'jadx':'1.5.6','apktool':'3.0.3'},'scope':'Static decompilation of user-supplied APK; no login or playback. Export masks credential-like literals. Decompiled code is not original source.'}
manifest=work/'apktool/AndroidManifest.xml'
if manifest.exists():
 m=ET.parse(manifest).getroot();a='{http://schemas.android.com/apk/res/android}'
 report['package']=m.get('package');report['permissions']=[x.get(a+'name') for x in m.findall('uses-permission')]
 report['activities']=[x.get(a+'name') for x in m.findall('.//activity')]
 report['services']=[x.get(a+'name') for x in m.findall('.//service')]
patterns={'intercept':r'shouldInterceptRequest|onLoadResource|shouldOverrideUrlLoading','hls':r'#EXT-X|M3U8|m3u8|HlsDownloader|HlsMediaSource','download':r'DownloadManager|ACTION_OPEN_DOCUMENT_TREE|Content-Range|contentLength\(|Range"','vk-search':r'video\.search|vkvideo\.ru|video_ext\.php|search_video','player':r'PlayerView|ExoPlayer|MediaPlayer|VLC|IJK|ijkplayer','security':r'onReceivedSslError|\.proceed\(|addJavascriptInterface|setWebContentsDebuggingEnabled'}
java=list((work/'jadx/sources').rglob('*.java'))
smali=list((work/'apktool').rglob('*.smali'))
report['java_files']=len(java);report['smali_files']=len(smali)
report['dex_files']=[n for n in zipfile.ZipFile(root/'org.xjiop.vkvideoapp-568.apk').namelist() if re.match(r'classes\d*\.dex$',n)]
report['hits']={}
for group,pattern in patterns.items():
 hits=[]
 for p in java:
  s=p.read_text(errors='replace');lines=[i+1 for i,line in enumerate(s.splitlines()) if re.search(pattern,line)]
  if lines:hits.append({'file':str(p.relative_to(work/'jadx/sources')),'lines':lines[:35]})
 report['hits'][group]=hits
report['jadx_warnings']={k:sum(p.read_text(errors='replace').count(k) for p in java) for k in ['Method not decompiled','JADX ERROR','JADX WARN']}
try:
 with urllib.request.urlopen('https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/maven-metadata.xml',timeout=25) as r:m=ET.fromstring(r.read())
 report['okhttp_maven_release']=m.findtext('versioning/release');report['okhttp_maven_last_updated']=m.findtext('versioning/lastUpdated')
except Exception as e:report['okhttp_metadata_error']=type(e).__name__
redactions=0
def sanitize(text):
 global redactions
 text,n=re.subn(r'"[A-Za-z0-9_+/=-]{48,}"','"REDACTED_OPAQUE_LITERAL"',text);redactions+=n
 text,n=re.subn(r'(?i)((?:access_token|client_secret|api_key|password)=)[^&\s"<>]+',r'\1REDACTED',text);redactions+=n
 text,n=re.subn(r'(?im)((?:secret|token|password|apiKey)\w*\s*=\s*)"[^"\n]{12,}"',r'\1"REDACTED_CREDENTIAL_LITERAL"',text);redactions+=n
 return text
out=root/'handoff/VKVIDEO-568-decompiled-review.zip'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for folder in ['jadx','apktool']:
  for p in sorted((work/folder).rglob('*')):
   if p.is_file() and p.suffix.lower() in ('.java','.smali','.xml','.json','.js','.html','.css','.txt','.yml','.properties'):
    z.writestr(str(p.relative_to(work)),sanitize(p.read_text(errors='replace')))
report['masked_literal_count']=redactions
report['export_sha256']=hashlib.sha256(out.read_bytes()).hexdigest()
report['export_bytes']=out.stat().st_size
(root/'handoff/VKVIDEO-568-decompilation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
Path(str(out)+'.sha256').write_text(report['export_sha256']+'  handoff/'+out.name+'\n')
print(json.dumps({k:v for k,v in report.items() if k!='hits'},ensure_ascii=False,indent=2))
