# YORU Android Java 4.12.3 — актуальное состояние

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Активное направление

Текущий продукт — стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не является активным направлением: модуль `yoru-android/kotlinapp` удалён, `settings.gradle` включает только `:app`, корневые Kotlin Gradle-плагины удалены, workflow собирает только Java APK.

## Итог 4.12.3

- Версия: `versionName 4.12.3`, `versionCode 46`.
- Release APK: `apk-output/YORU-4.12.3-release.apk`.
- APK SHA256: `8a1b4461866cd17f24bd303b98c1efd1cda0247f2336feb3bc76cc6640f820d0`.
- Успешный GitHub Actions run: `34043897672`.
- Source commit: `c6a1d26` (`Restore instant Java UI and turbo calendar`).
- APK commit: `05f7800` (`Add built YORU 4.12.3 release APK [skip ci]`).

## Исправлено после 4.12.2

- Календарь получил готовые buckets `filter -> day -> events`, поэтому переключение `Избранное`/`Все`/`Скоро` больше не сканирует весь список событий и не обращается к `SecureStore` на каждый tap.
- `MainActivity` кэширует `CalendarScreen`: при возврате на вкладку календаря экран не создаётся заново и не запускает новый первичный load.
- `DetailsActivity.render()` снова лёгкий: нет синхронного `DownloadManager`, нет repeated `progress/favorite/bucket` на каждую строку серии.
- Офлайн-статусы серий догружаются отдельной задачей и потом обновляют UI.
- Карточка деталей стала двухступенчатой: `quickDetails()` отдаёт описание/метаданные быстро, полный `details(..., true)` догружает серии/маршруты после этого.
- Shikimori franchise lookup добавлен к связанным аниме: direct related + `/api/animes/{id}/franchise` + batch GraphQL enrichment.
- UI больше не режет связанные аниме на 36 элементов, показывает весь список из franchise/related.
- Turbo-профиль: hidden discovery не режет безопасные маршруты по mobile/data-saver, общий IO-пул расширен до 8–12 потоков.
- C++/NDK не добавлялись.

## Проверки

- `git diff --check`: OK.
- Локальная Gradle-сборка в Arena невозможна из-за отсутствия `java/JAVA_HOME`.
- GitHub Actions `34043897672`: success, шаг `Build Java release APK` прошёл.
- `sha256sum -c apk-output/YORU-4.12.3-release.apk.sha256`: OK.

## Жёсткие правила будущего продолжения

- Работать только с Java Android `app.yoru.mobile`, пока пользователь явно не поменяет направление.
- Не возвращать Kotlin, Forge/toolchain, Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальная команда перевода/дубляжа; технический маршрут не должен называться озвучкой.
- Выбранная/дефолтная озвучка — предпочтение для скорости, но отсутствие этой озвучки не должно ломать playback/download.
- Будущие серии должны быть визуально непроигрываемыми и показывать дату выхода/уточнение, а не обычную обложку.
- Календарь избранного должен оставаться быстрым: не возвращать repeated JSON scans, repeated filter scans и последовательные сетевые details по каждому избранному.
