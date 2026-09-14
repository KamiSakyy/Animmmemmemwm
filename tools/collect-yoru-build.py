from pathlib import Path
import hashlib
import json
import os
import zipfile

root = Path(__file__).resolve().parents[1]
apk = root / "yoru-android/app/build/outputs/apk/debug/app-debug.apk"
data = apk.read_bytes()
with zipfile.ZipFile(apk) as package:
    stored = all(entry.compress_type == zipfile.ZIP_STORED for entry in package.infolist())
if not stored:
    raise RuntimeError("APK contains compressed entries")
report = {"minification_enabled": False, "resource_shrinking_enabled": False, "all_apk_entries_stored": stored, "source_commit": os.environ["GITHUB_SHA"], "run_id": os.environ["GITHUB_RUN_ID"], "assembled": True, "apk_bytes": len(data), "apk_sha256": hashlib.sha256(data).hexdigest(), "tests_executed": False, "device_checks_executed": False}
(root / "handoff/YORU-4.20.0-dev.10-build-report.json").write_text(json.dumps(report, indent=2) + "\n")
