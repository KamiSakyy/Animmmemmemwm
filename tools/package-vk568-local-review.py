import hashlib,json,re,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
work=root/'workspace-output/vk568-local/build'
report=json.loads((root/'handoff/VKVIDEO-568-local-progress.json').read_text())
assert report.get('finished') and report['classes']==report['java_ok']
masked=0
def clean(s):
 global masked
 s,n=re.subn(r'"[A-Za-z0-9_+/=-]{40,}"','"REDACTED_OPAQUE_LITERAL"',s);masked+=n
 s,n=re.subn(r'(?i)((?:access_token|client_secret|api_key|password)=)[^&\s"<>]+',r'\1REDACTED',s);masked+=n
 s,n=re.subn(r'(?im)((?:secret|token|password|apiKey)\w*\s*=\s*)"[^"\n]{12,}"',r'\1"REDACTED_CREDENTIAL_LITERAL"',s);masked+=n
 s,n=re.subn(r'AIza[0-9A-Za-z_-]{35}', 'REDACTED_API_KEY', s);masked+=n
 s,n=re.subn(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', 'REDACTED_JWT', s);masked+=n
 s,n=re.subn(r'"[a-fA-F0-9]{24,}"', '"REDACTED_HEX_LITERAL"', s);masked+=n
 return s
out=root/'handoff/VKVIDEO-568-local-decompiled-review.zip'
count=0
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
 for p in sorted(work.rglob('*')):
  if p.is_file() and p.suffix in ('.java','.dexasm','.xml','.json'):
   z.writestr(p.relative_to(work).as_posix(),clean(p.read_text(errors='replace')));count+=1
 z.writestr('README.ru.md','Декомпиляция предоставленного пользователем APK 568. Движок Androguard DAD; JADX/Apktool в CI не запускались из-за Billing. Все классы обработаны, но результат не является оригинальными исходниками, Java не гарантированно компилируется. dexasm — инструкции Dalvik с адресами, не собираемый smali. Нативный C/C++ исходник не восстановлен. Для приватного анализа; лицензия на перенос чужого кода в YORU этим не предоставляется. Непрозрачные длинные литералы и похожие на credentials строки маскируются. Не запускать извлечённый код и не использовать чужие ключи. Вся реализация YORU должна быть независимой.\n')
report['export_file']=out.name;report['export_bytes']=out.stat().st_size;report['export_entries']=count+1;report['masked_literals']=masked
report['export_sha256']=hashlib.sha256(out.read_bytes()).hexdigest()
report['jadx_apktool_ci']={'run':'34813839601','started':False,'reason':'GitHub account payments/spending limit'}
(root/'handoff/VKVIDEO-568-analysis-evidence.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
Path(str(out)+'.sha256').write_text(report['export_sha256']+'  handoff/'+out.name+'\n')
