# YORU Android Java 4.11.0 — актуальное состояние

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Активное направление

Текущий продукт — стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. База продолжена от последнего Java-релиза 4.10.0. Kotlin больше не является активным направлением: модуль `yoru-android/kotlinapp` удалён, `settings.gradle` включает только `:app`, workflow собирает только Java APK.

## Итог 4.11.0

- Версия: `versionName 4.11.0`, `versionCode 42`.
- Release APK: `apk-output/YORU-4.11.0-release.apk`.
- APK SHA256: `b8b0098c8c6fbfe118169d98aa2f6785b0bf2d1fa9bc2857f408efd48737df49`.
- Успешный GitHub Actions run: `34038631842`.
- Source commit: `f91a5cf` (`Return YORU Java 4.11.0`).
- APK commit: `b88b981` (`Add built YORU 4.11.0 release APK [skip ci]`).

## Что изменено

- Kotlin удалён из исходников и CI, старые Kotlin APK/source handoff удалены из активной ветки.
- `settings.gradle` теперь Java-only: только `include ':app'`.
- `.github/workflows/build-apk.yml` собирает `:app:assembleRelease` и сохраняет `YORU-4.11.0-release.apk`.
- Технические пользовательские строки `YORU Source`/`YORU Auto` заменены на простой `YORU`.
- `AniDUB` приведён к корректному виду как реальная озвучка.
- `music` больше не показывается как `Клип`, чтобы не возвращать rejected Clips-семантику.
- Если выбрана реальная озвучка, она становится строгим быстрым путём: без повторного диалога и без перебора чужих голосов.
- `SourceEngine` получил voice-aware boost для AniDUB/AniLibria.TV/AnimeVost/AniMedia и похожих реальных озвучек.
- `ApiRepository.yoruFoundSources()` уменьшает количество маршрутов на слабой сети и при выбранной озвучке.
- `PlayerActivity` добавил timeline/SeekBar поверх нативного видео и отключает prewarm следующей серии без выбранной реальной озвучки.
- `DownloadActions` показывает понятные варианты качества/озвучки/размера и Wi‑Fi-only формулировку без внутренних маршрутов.

## Проверки

- `git diff --check` перед коммитом: OK.
- Локальная Gradle-сборка в Arena невозможна из-за отсутствия `java/JAVA_HOME`.
- GitHub Actions `34038631842`: success, шаг `Build Java release APK` прошёл.
- `sha256sum -c apk-output/YORU-4.11.0-release.apk.sha256`: OK.

## Жёсткие правила будущего продолжения

- Работать только с Java Android `app.yoru.mobile`, пока пользователь явно не поменяет направление.
- Не возвращать Kotlin, Forge/toolchain, Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальная команда перевода/дубляжа; технический маршрут не должен называться озвучкой.
- Приоритет: скорость, слабая сеть, экономия трафика, один нативный YORU Player, понятные загрузки и минимум лишних экранов.
