from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
source = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=root, text=True).strip()
output = root / "workspace-output/yoru-final"
output.mkdir(parents=True, exist_ok=True)
apk = output / "YORU-4.42.0.apk"
shutil.copyfile(root / "yoru-android/app/build/outputs/apk/debug/app-debug.apk", apk)
with zipfile.ZipFile(apk) as bundle:
    if any(entry.compress_type != zipfile.ZIP_STORED for entry in bundle.infolist()):
        raise RuntimeError("APK compression must remain disabled")
report = {
    "version": "4.42.0", "version_code": 104, "source_commit": source,
    "run_id": os.environ.get("GITHUB_RUN_ID", ""), "assembled": True,
    "apk_bytes": apk.stat().st_size, "apk_sha256": hashlib.sha256(apk.read_bytes()).hexdigest(),
    "build_profile": "existing verifyOnSdk36 ownerSignedApk assembleStoredDebug",
    "sdk_profile_changed": False, "compile_sdk": 36, "target_sdk": 36,
    "package": "app.yoru.mobile.debug", "okhttp_android": "5.4.0",
    "minification_enabled": False, "resource_shrinking_enabled": False,
    "all_apk_entries_stored": True, "tests_executed": False,
    "device_checks_executed": False, "provider_checks_executed": False
}
receipt = (json.dumps(report, ensure_ascii=False, indent=2) + "\n").encode()
(output / "BUILD-RECEIPT.json").write_bytes(receipt)
files = subprocess.check_output(["git", "ls-tree", "-r", "--name-only", source, "yoru-android"], cwd=root, text=True).splitlines()
files += ["AGENTS.md", "handoff/YORU-4.20.0-FINAL.ru.md", ".github/workflows/deliver-yoru-major.yml", ".github/workflows/build-apk.yml", "tools/package-yoru-major.py"]
manifest = {}
archive = output / "YORU-4.42.0-source.zip"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
    for name in sorted(set(files)):
        path = Path(name)
        if any(part in {"owner-signing", ".git", ".gradle", "build", ".idea"} for part in path.parts) or path.suffix.lower() in {".apk", ".jks", ".keystore"} or path.name in {"local.properties", "signing.properties"}:
            continue
        content = subprocess.check_output(["git", "show", source + ":" + name], cwd=root)
        manifest[name] = hashlib.sha256(content).hexdigest()
        info = zipfile.ZipInfo(name, (2026, 9, 14, 0, 0, 0))
        info.create_system = 3
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = (0o100755 if name.endswith("/gradlew") else 0o100644) << 16
        bundle.writestr(info, content)
    bundle.writestr("BUILD-RECEIPT.json", receipt)
    manifest["BUILD-RECEIPT.json"] = hashlib.sha256(receipt).hexdigest()
    bundle.writestr("SOURCE-MANIFEST.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")
for file in (apk, archive):
    file.with_name(file.name + ".sha256").write_text(hashlib.sha256(file.read_bytes()).hexdigest() + "  " + file.name + "\n")
