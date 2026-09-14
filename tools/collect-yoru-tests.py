from pathlib import Path
import json
import os
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
profile = sys.argv[1]
if profile not in {"jvm", "android"}:
    raise ValueError("Unsupported verification profile")
reports = sorted((root / "yoru-android/app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
if not reports:
    raise RuntimeError("JUnit reports were not produced")
suites = []
for path in reports:
    suite = ET.parse(path).getroot()
    suites.append({"name": suite.get("name"), **{key: int(suite.get(key, "0")) for key in ("tests", "failures", "errors", "skipped")}})
result = {key: sum(suite[key] for suite in suites) for key in ("tests", "failures", "errors", "skipped")}
result["suites"] = suites
result["compile_sdk"] = 36
result["transport"] = "okhttp-jvm:5.5.0" if profile == "jvm" else "okhttp Android:5.4.0"
path = root / "handoff/YORU-4.20.0-dev.3-verification-tests.json"
commit = os.environ.get("GITHUB_SHA", "local")
previous = json.loads(path.read_text()) if path.exists() else {}
if previous.get("source_commit") != commit:
    previous = {"source_commit": commit, "run_id": os.environ.get("GITHUB_RUN_ID", "local"), "profiles": {}}
previous["profiles"][profile] = result
path.write_text(json.dumps(previous, ensure_ascii=False, indent=2) + "\n")
if result["tests"] == 0 or result["failures"] or result["errors"]:
    raise RuntimeError("JUnit verification failed")
