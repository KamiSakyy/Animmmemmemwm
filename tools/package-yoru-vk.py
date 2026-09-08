from pathlib import Path
import hashlib
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
OUTPUT=ROOT/'handoff/YORU-VK-0.2.0-source.zip'
tracked=subprocess.check_output(['git','ls-files','-z','yoru-vk-android'],cwd=ROOT).decode().split('\0')
files=[ROOT/name for name in tracked if name]
files += [ROOT/'.github/workflows/build-yoru-vk.yml', ROOT/'.github/workflows/probe-vk-web.yml', ROOT/'tools/probe-vk-web.py', Path(__file__)]
for name in ['handoff/YORU-VK-0.2.0-BUILD.ru.md','handoff/YORU-VK-0.2.0-build.json','handoff/YORU-VK-web-probe-2026-09-09.json','handoff/YORU-VK-web-verified-2026-09-09.json']:
    path=ROOT/name
    if path.exists():files.append(path)
with zipfile.ZipFile(OUTPUT,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as z:
    for p in sorted(set(files)):
        relative=p.relative_to(ROOT)
        assert not any(part in ('build','.gradle','owner-signing','.git') for part in relative.parts)
        assert p.suffix not in ('.apk','.jks','.keystore','.zip')
        z.write(p,relative.as_posix())
sha=hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
Path(str(OUTPUT)+'.sha256').write_text(f'{sha}  {OUTPUT.relative_to(ROOT)}\n')
print('Source files:',len(files),'bytes:',OUTPUT.stat().st_size,'sha256:',sha)
