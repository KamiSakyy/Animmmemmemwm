# YORU Android Java 4.12.4 — актуальное состояние

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Активное направление

Текущий продукт — стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не является активным направлением: активная сборка включает только `:app`. Forge/toolchain в релизе 4.12.4 не используется.

## Итог 4.12.4

- Версия: `versionName 4.12.4`, `versionCode 47`.
- Release APK: `apk-output/YORU-4.12.4-release.apk`.
- APK SHA256: `71d3d3bb92df4858ec62b2f5ed5867ac55c820701fa34ff0db3c721ff3dde24c`.
- Успешный GitHub Actions run: `34046274405`.
- Source commit перед APK: `a4e35e9` (`Fix calendar cache lambda capture`).
- APK commit: `c641f3c` (`Add built YORU 4.12.4 release APK [skip ci]`).

## Исправлено после 4.12.3

- Убран лишний стартовый fade-from-black в `Ui.base()`.
- `MainActivity` больше не делает повторный первый render из `onResume()` сразу после `onCreate()`.
- Тяжёлые startup-задачи перенесены после первого кадра: проверка подлинности, WebView-флаг, update-check, protection refresh и warm-cache.
- Добавлен `YoruCache.java`: SQLite fast-cache для `details`, `schedule`, `franchise`, `offline`, `progress` с TTL/лимитами/trim.
- `ApiRepository.quickDetails()` читает/пишет SQLite и unified identity cache по MAL/Anilist/KP/source key.
- `ApiRepository.prefetchQuickDetails()` используется для видимых карточек и стартового warmup.
- `CalendarScreen` переведён на `RecyclerView`, читает SQLite schedule cache до сети и тихо обновляет расписание избранного.
- `DetailsActivity` переведён на `RecyclerView` для списка серий, сохраняет быстрые офлайн-статусы отдельно и отменяет старые detail/download задачи при уходе.
- `MainActivity` получил screen-cache для `Главная`/`Каталог`/`Коллекция`/`Календарь`, cache key и cancellation старых task/future.
- Добавлен быстрый offline-index в `DownloadHub`: UI больше не должен сканировать `DownloadIndex` на каждую строку серии.
- Франшиза получила SQLite-cache, stable sort, фильтры `Все/Сезоны/Фильмы/OVA/Спешлы`, кнопку `Смотреть по порядку франшизу` и кнопку продолжения к следующему тайтлу.
- Hidden discovery теперь использует максимум маршрутов в рамках дедлайна, без раннего stop по количеству найденных 3/5 вариантов.
- Плеер больше не режет качество/буфер под mobile/data-saver default и заранее pre-check следующей серии.
- `ImageLoader` получил расширенный image-pool; poster disk-cache остаётся ограниченным.
- Добавлен скрытый `sourceDiagnostics()` без пользовательского вывода технических маршрутов.

## Проверки

- `git diff --check`: OK перед коммитами.
- Локальная Gradle-сборка в Arena невозможна из-за отсутствия `java/JAVA_HOME`.
- CI compile error #1 был исправлен: Java 11 regex escaping.
- CI compile error #2 был исправлен: effectively-final lambda capture в `CalendarScreen`.
- GitHub Actions `34046274405`: success, шаг `Build Java release APK` прошёл.
- `sha256sum -c apk-output/YORU-4.12.4-release.apk.sha256`: OK.

## Жёсткие правила будущего продолжения

- Работать только с Java Android `app.yoru.mobile`, пока пользователь явно не поменяет направление.
- Не возвращать Kotlin, Forge/toolchain, Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальная команда перевода/дубляжа; технический маршрут не должен называться озвучкой.
- Выбранная/дефолтная озвучка — предпочтение для скорости, но отсутствие этой озвучки не должно ломать playback/download.
- Будущие серии должны быть визуально непроигрываемыми и показывать дату выхода/уточнение, а не обычную обложку.
- Календарь избранного должен оставаться быстрым: не возвращать repeated JSON scans, repeated filter scans и последовательные сетевые details по каждому избранному.
- Cache должен оставаться bounded: TTL, лимиты, trim, без бесконечного stale-cache и без лишнего расхода интернета.
