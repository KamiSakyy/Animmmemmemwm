from pathlib import Path
import hashlib
import json
import os

root = Path(__file__).resolve().parents[1]
apk = root / "yoru-android/app/build/outputs/apk/debug/app-debug.apk"
data = apk.read_bytes()
report = {"source_commit": os.environ["GITHUB_SHA"], "run_id": os.environ["GITHUB_RUN_ID"], "assembled": True, "apk_bytes": len(data), "apk_sha256": hashlib.sha256(data).hexdigest(), "tests_executed": False, "device_checks_executed": False}
(root / "handoff/YORU-4.20.0-dev.7-build-report.json").write_text(json.dumps(report, indent=2) + "\n")
