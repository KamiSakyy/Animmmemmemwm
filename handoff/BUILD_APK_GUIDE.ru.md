# YORU — как собрать release APK

## Быстрый путь через GitHub Actions

1. Для этой Arena-сессии работать только в ветке `arena/01a07147-animmmemmemwm`. Не переключаться и не пушить в другие ветки.
2. Workflow находится в `.github/workflows/build-apk.yml`.
3. Workflow должен собирать только release APK командой:

```bash
./gradlew --no-daemon :app:assembleRelease
```

4. Отладочную сборку не делать.
5. После успешной сборки workflow копирует APK в `apk-output/` и создаёт `.sha256`.
6. Не добавлять приватные ключи в чат и не просить токены.

## Локальная сборка

Нужны JDK 17, Android SDK и Gradle wrapper проекта.

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Готовый файл:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

## Проверки перед APK

```bash
/tmp/yoru-check-venv/bin/python - <<'PY'
import pathlib, javalang
files=list(pathlib.Path('yoru-android/app/src/main/java').rglob('*.java'))
for p in files:
    javalang.parse.parse(p.read_text())
print('JAVA_PARSE_OK', len(files))
PY

git diff --check
```

Дополнительно вручную проверить, что в пользовательских экранах и документации нет старых лишних разделов, игровых счётчиков, расплывчатых названий качества, нерабочих направлений, системных диалогов и отладочной сборки.
