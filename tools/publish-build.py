#!/usr/bin/env python3
"""Write small CI provenance reports; never include signing material."""
import datetime
import json
import os
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
out = root / 'apk-output'
out.mkdir(exist_ok=True)
counts = dict(tests=0, failures=0, errors=0, skipped=0)
for report in (root / 'yoru-android/app/build/test-results').glob('**/TEST-*.xml'):
    suite = ET.parse(report).getroot()
    for key in counts:
        counts[key] += int(suite.get(key, '0'))
lint = []
for report in (root / 'yoru-android/app/build/reports').glob('lint-results*.xml'):
    for issue in ET.parse(report).getroot().findall('issue'):
        if issue.get('severity') in ('Error', 'Fatal'):
            lint.append({'id': issue.get('id'), 'message': issue.get('message'),
                         'locations': [x.attrib for x in issue.findall('location')]})
status = {
    'source_commit': os.environ.get('BUILD_SHA', ''),
    'workflow_run': os.environ.get('GITHUB_RUN_ID', ''),
    'branch': os.environ.get('GITHUB_REF_NAME', ''),
    'outcome': os.environ.get('BUILD_OUTCOME', 'unknown'),
    'gradle_exit_code': os.environ.get('BUILD_EXIT_CODE', ''),
    'recorded_at_utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
    'requested_release': os.environ.get('PUBLISH_APK') == 'true',
    'sdk_platform_36_present': (Path(os.environ.get('ANDROID_HOME', '/nonexistent')) / 'platforms/android-36/android.jar').is_file(),
    'unit_tests': counts,
    'lint_errors': lint,
    'device_performance_tested': False,
}
(out / 'BUILD-STATUS.json').write_text(json.dumps(status, ensure_ascii=False, indent=2) + '\n')
log = (root / 'build.log').read_text(errors='replace') if (root / 'build.log').exists() else 'Gradle was not reached; see workflow steps.'
# Only diagnostic output. Drop signed URL queries if a dependency/tool echoes one.
log = re.sub(r'(https?://[^\s?]+)\?[^\s]+', r'\1?[redacted]', log)
(out / 'BUILD-LOG.txt').write_text(log[-45000:].rstrip() + '\n')
print(json.dumps(status, ensure_ascii=False))
