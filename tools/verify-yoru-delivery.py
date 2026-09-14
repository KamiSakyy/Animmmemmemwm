from pathlib import Path
import hashlib
import json
import re
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
version = "4.20.0-dev.6"
report = json.loads((root / f"handoff/YORU-{version}-build-report.json").read_text())
source = report["source_commit"]
if not re.fullmatch(r"[0-9a-f]{40}", source):
    raise ValueError("Invalid source revision")
if not report.get("assembled"):
    raise ValueError("APK was not assembled")
if subprocess.run(["git", "cat-file", "-e", source + "^{commit}"], cwd=root, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode != 0:
    subprocess.run(["git", "fetch", "--no-tags", "origin", source, "--depth=1"], cwd=root, check=True, stdout=subprocess.DEVNULL)
archive = root / f"handoff/YORU-{version}-source.zip"
apk = root / f"apk-output/YORU-{version}-compat-sdk36-okhttp5.4.apk"
if hashlib.sha256(apk.read_bytes()).hexdigest() != report["apk_sha256"]:
    raise ValueError("APK does not match the build report")
for path in (archive, apk):
    expected = Path(str(path) + ".sha256").read_text().split()[0]
    if hashlib.sha256(path.read_bytes()).hexdigest() != expected:
        raise ValueError("Delivery checksum mismatch")
with zipfile.ZipFile(archive) as bundle:
    if bundle.testzip() is not None:
        raise ValueError("Damaged archive")
    manifest = json.loads(bundle.read("SOURCE-MANIFEST.json"))
    for name in bundle.namelist():
        path = Path(name)
        if any(part in {"owner-signing", ".git", ".gradle", "build"} for part in path.parts) or path.suffix in {".jks", ".keystore", ".apk"}:
            raise ValueError("Private or generated file in source archive")
        if name == "SOURCE-MANIFEST.json":
            continue
        content = bundle.read(name)
        if hashlib.sha256(content).hexdigest() != manifest[name]:
            raise ValueError("Source manifest mismatch")
        if name.startswith("yoru-android/"):
            built = subprocess.check_output(["git", "show", source + ":" + name], cwd=root)
            if content != built:
                raise ValueError("Archive does not match built Android sources")
