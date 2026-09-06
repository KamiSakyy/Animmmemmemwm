# YORU Android Java 4.12.0 — актуальное состояние

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Активное направление

Текущий продукт — стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не является активным направлением: модуль `yoru-android/kotlinapp` удалён, `settings.gradle` включает только `:app`, корневые Kotlin Gradle-плагины удалены, workflow собирает только Java APK.

## Итог 4.12.0

- Версия: `versionName 4.12.0`, `versionCode 43`.
- Release APK: `apk-output/YORU-4.12.0-release.apk`.
- APK SHA256: `2e6f7e4ab6cd91d004dec539bc0f8689990a868dd00d2cff12a83c406ef706b3`.
- Успешный GitHub Actions run: `34040316252`.
- Source commit: `5315bdf` (`Release YORU Java 4.12.0 scheduling and fallback`).
- APK commit: `f157fa0` (`Add built YORU 4.12.0 release APK [skip ci]`).

## Что изменено в 4.12.0

- `Anime` получил отдельные поля `episodesAired` и `nextEpisodeAt`, сериализацию в JSON и отображение `cardMeta()`.
- Shikimori GraphQL fields расширены до `episodesAired nextEpisodeAt`; детали дополнительно обогащают тайтл через `enrichSchedule()`.
- YORU-каталог использует Shikimori как быстрый метаданный слой, поэтому карточки видят статус, total/aired и дату следующей серии.
- Карточки показывают `Онгоинг/Закончен/Анонс` и `Вышло X из Y`.
- Экран тайтла показывает статус, количество вышедших/плановых серий и `следующая: дата`, если есть точный `nextEpisodeAt`.
- `appendFutureEpisodes()` добавляет future-заглушки для невышедших эпизодов; первая получает точную дату из `nextEpisodeAt`, остальные показывают `Дата уточняется`.
- Future-серии не открываются и не скачиваются; в превью вместо обложки показывается дата/ожидание.
- `loadYoruEpisode()` больше не считает выбранную озвучку жёстким финальным фильтром: если совпадений нет, собирает доступные голоса и отключает strict onlyPreferred для этой попытки.
- `PlayerActivity` делает fallback при пустых voiceGroups, rescue-playback пробует любой доступный рабочий вариант после выбранного голоса, future-серии пропускаются в next/prewarm.
- `DownloadActions` и `ApiRepository.downloadOptions()` делают fallback на доступные варианты, если выбранной озвучки нет, и не предлагают future-серии.
- Anix/Sekai route сортирует выбранный голос первым, но не отбрасывает остальные голоса полностью.
- Пользовательские технические подписи `YORU Prime/Reserve/Max` очищены до `YORU`; real voice labels остаются реальными командами.

## Проверки

- `git diff --check`: OK.
- Локальная Gradle-сборка в Arena невозможна из-за отсутствия `java/JAVA_HOME`.
- GitHub Actions `34040316252`: success, шаг `Build Java release APK` прошёл.
- `sha256sum -c apk-output/YORU-4.12.0-release.apk.sha256`: OK.

## Жёсткие правила будущего продолжения

- Работать только с Java Android `app.yoru.mobile`, пока пользователь явно не поменяет направление.
- Не возвращать Kotlin, Forge/toolchain, Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальная команда перевода/дубляжа; технический маршрут не должен называться озвучкой.
- Выбранная/дефолтная озвучка — предпочтение для скорости, но отсутствие этой озвучки не должно ломать playback/download.
- Будущие серии должны быть визуально непроигрываемыми и показывать дату выхода/уточнение, а не обычную обложку.
- Приоритет: скорость, слабая сеть, экономия трафика, один нативный YORU Player, понятные загрузки и минимум лишних экранов.
