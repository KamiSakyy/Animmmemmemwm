from pathlib import Path
import hashlib
import zipfile

root=Path(__file__).resolve().parents[1]
version='4.19.1'
output=root/'handoff'/f'YORU-{version}-source.zip'
paths=[root/'AGENTS.md',root/'README.md',root/'.github/workflows/build-apk.yml',root/'tools/package-yoru-source.py',root/'tools/check-yoru-update.py',root/'handoff'/f'YORU-{version}-BUILD.ru.md']
for name in ['build.gradle','settings.gradle','gradle.properties','gradlew','gradlew.bat','CHANGELOG.ru.md']:
    paths.append(root/'yoru-android'/name)
paths.extend((root/'yoru-android/gradle').rglob('*'))
paths.extend((root/'yoru-android/app/src').rglob('*'))
paths.extend([root/'yoru-android/app/build.gradle',root/'yoru-android/app/proguard-rules.pro'])
with zipfile.ZipFile(output,'w',zipfile.ZIP_DEFLATED,compresslevel=9) as archive:
    for path in sorted(set(paths)):
        if path.is_file():
            name=path.relative_to(root).as_posix()
            assert not any(x in name for x in ['owner-signing','signing.properties','.jks','.apk','node_modules'])
            archive.write(path,name)
with zipfile.ZipFile(output) as archive:
    assert archive.testzip() is None
checksum=hashlib.sha256(output.read_bytes()).hexdigest()
Path(str(output)+'.sha256').write_text(checksum+'  '+output.relative_to(root).as_posix()+'\n')
print(output.name,checksum)
