from pathlib import Path
import hashlib
import json
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
version = "4.20.0-dev.1"
files = subprocess.check_output(["git", "ls-files", "yoru-android"], cwd=root, text=True).splitlines()
files += [
    ".github/workflows/build-apk.yml",
    "AGENTS.md",
    "handoff/YORU-PRODUCTION-36-STATUS-2026-09-14.ru.md",
    f"handoff/YORU-{version}-BUILD.ru.md",
    f"handoff/YORU-{version}-ci-jobs.json",
    "tools/package-yoru-dev-source.py",
]
for name in files:
    path = Path(name)
    if any(part in {"owner-signing", "build", ".gradle", ".git"} for part in path.parts):
        raise RuntimeError("Private or generated path in source list")
    if path.suffix in {".apk", ".jks", ".keystore"} or path.name == "local.properties":
        raise RuntimeError("Private or generated file in source list")
    if not (root / path).is_file():
        raise RuntimeError("Missing source file")
archive = root / "handoff" / f"YORU-{version}-source.zip"
manifest = {}
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as output:
    for name in sorted(set(files)):
        data = (root / name).read_bytes()
        manifest[name] = hashlib.sha256(data).hexdigest()
        info = zipfile.ZipInfo(name, (2026, 9, 14, 0, 0, 0))
        info.create_system = 3
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = (0o100755 if name.endswith("/gradlew") else 0o100644) << 16
        output.writestr(info, data)
    output.writestr("SOURCE-MANIFEST.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
checksum = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix(".zip.sha256").write_text(f"{checksum}  {archive.relative_to(root)}\n")
with zipfile.ZipFile(archive) as output:
    if output.testzip() is not None:
        raise RuntimeError("Archive validation failed")
    for name, expected in manifest.items():
        if hashlib.sha256(output.read(name)).hexdigest() != expected:
            raise RuntimeError("Source manifest mismatch")
