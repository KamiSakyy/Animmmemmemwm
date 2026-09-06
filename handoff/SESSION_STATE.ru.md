# YORU Android Java 4.12.1 — актуальное состояние

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Активное направление

Текущий продукт — стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не является активным направлением: модуль `yoru-android/kotlinapp` удалён, `settings.gradle` включает только `:app`, корневые Kotlin Gradle-плагины удалены, workflow собирает только Java APK.

## Итог 4.12.1

- Версия: `versionName 4.12.1`, `versionCode 44`.
- Release APK: `apk-output/YORU-4.12.1-release.apk`.
- APK SHA256: `54013987f9d409ed3a621375db822805bb0483931575b97b3c6bf9068d46c269`.
- Успешный GitHub Actions run: `34041378861`.
- Source commit: `1f81b2f` (`Fix calendar favorites and future episode dates`).
- APK commit: `44fcc99` (`Add built YORU 4.12.1 release APK [skip ci]`).

## Исправлено после 4.12.0

- Future-серии теперь определяются по `episodesAired`, даже если источник заранее создал технические строки серий выше вышедшего числа.
- `appendFutureEpisodes()` принудительно помечает все серии выше `episodesAired` как future, очищает их streams/variants/poster и задаёт дату выхода.
- `DetailsActivity` имеет дополнительную UI-защиту: если `episode.number > anime.episodesAired`, показывается дата/уточнение, а не `Видео-превью`.
- `applyEpisodeVisuals()` больше не подставляет визуалы future-сериям.
- `CalendarScreen` вызывает `airingSchedule(21, favorites)` и отдельно получает расписание избранных тайтлов.
- `ApiRepository` добавил `appendFavoriteAirings()`, batch-запрос Shikimori по MAL/Shikimori ID и поиск ID по названию для старых избранных.
- `SecureStore.favorite()`/`bucket()`/`updateFavoriteEpisodes()` теперь находят одну и ту же карточку через ID/название, а не только exact `source:id`.
- `SecureStore.progress()` получил такой же identity-match для фильтра `Новые для меня`.
- `EpisodeUpdateReceiver` сравнивает released/playable серии, а не total, и чинит старый завышенный `episodesSeen`.
- Календарь показывает 21 день и автоматически выбирает ближайший день с событием по текущему фильтру.

## Проверки

- `git diff --check`: OK.
- Локальная Gradle-сборка в Arena невозможна из-за отсутствия `java/JAVA_HOME`.
- GitHub Actions `34041378861`: success, шаг `Build Java release APK` прошёл.
- `sha256sum -c apk-output/YORU-4.12.1-release.apk.sha256`: OK.

## Жёсткие правила будущего продолжения

- Работать только с Java Android `app.yoru.mobile`, пока пользователь явно не поменяет направление.
- Не возвращать Kotlin, Forge/toolchain, Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальная команда перевода/дубляжа; технический маршрут не должен называться озвучкой.
- Выбранная/дефолтная озвучка — предпочтение для скорости, но отсутствие этой озвучки не должно ломать playback/download.
- Будущие серии должны быть визуально непроигрываемыми и показывать дату выхода/уточнение, а не обычную обложку.
- Календарь избранного должен использовать identity-match между источниками и отдельно проверять любимые тайтлы.
