# YORU Kotlin — как собрать release APK

## Быстрый путь через GitHub Actions

1. В этой Arena-сессии работать только в ветке `arena/01a07147-animmmemmemwm`.
2. Workflow: `.github/workflows/build-apk.yml`.
3. Текущий продукт — Kotlin-приложение `app.yoru.kotlin`; Java YORU остаётся архивной базой/референсом.
4. Release APK собирается командой:

```bash
cd yoru-android
./gradlew --no-daemon :kotlinapp:assembleRelease
```

5. Workflow копирует итог в:

```text
apk-output/YORU-Kotlin-1.2.0-release.apk
apk-output/YORU-Kotlin-1.2.0-release.apk.sha256
```

6. Debug APK не нужен. Приватные ключи/токены не спрашивать и не писать в чат.

## Локальная сборка

Нужны JDK 17, Android SDK и Gradle wrapper проекта.

```bash
cd yoru-android
./gradlew --no-daemon :kotlinapp:assembleRelease
```

Готовый локальный файл до копирования:

```text
yoru-android/kotlinapp/build/outputs/apk/release/kotlinapp-release.apk
```

## Проверки перед APK

```bash
git diff --check
cd yoru-android
./gradlew --no-daemon :kotlinapp:assembleRelease
```

В текущем sandbox локальный JDK может отсутствовать; тогда проверять через GitHub Actions и после успеха подтягивать APK:

```bash
git pull --rebase origin arena/01a07147-animmmemmemwm
sha256sum -c apk-output/YORU-Kotlin-1.2.0-release.apk.sha256
```

## Что нельзя ломать

- Не возвращать Forge/toolchain без явной просьбы пользователя.
- Не показывать технические route/source/player-имена как “озвучку”.
- Озвучка — только реальные команды: AniLibria.TV, AniDUB, AniMaunt, AnimeVost, AniStar & DEEP, Beyond:Studio, Dream Cast, AniMedia и т.п.
- Не добавлять VPN/VLESS/Xray/proxy/private keys/MP4-конвертацию.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
