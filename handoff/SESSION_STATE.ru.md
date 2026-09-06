# YORU Android Java 4.12.5 — актуальное состояние

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Активное направление

Текущий продукт — стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не является активным направлением: активная сборка включает только `:app`. Forge/toolchain в релизе 4.12.5 не используется.

## Итог 4.12.5

- Версия: `versionName 4.12.5`, `versionCode 48`.
- Release APK: `apk-output/YORU-4.12.5-release.apk`.
- APK SHA256: `970d31677e4973d952dd26f7e65cf03825dfde7d9d22c7c025ffa89f9a35932b`.
- Успешный GitHub Actions run: `34047539871`.
- Source commit перед APK: `422052a` (`Fix calendar scroll and remove startup waits`).
- APK commit: `92e1e20` (`Add built YORU 4.12.5 release APK [skip ci]`).

## Срочно исправлено после 4.12.4

- Исправлен календарь: интерфейс больше не “уезжает”, события/аниме видны, вертикальный scroll работает по всему экрану.
- `CalendarScreen` теперь наследуется от `ScrollView`; внутри единый `LinearLayout box` с шапкой, днями, фильтрами, status и списком событий.
- События календаря остаются на `RecyclerView`, но `nestedScrolling=false`, `wrap_content`, без внутреннего конфликтующего viewport.
- Убран принудительный `recycler.scrollToPosition(0)` при обновлении адаптера, чтобы календарь не прыгал.
- Сетевое обновление календаря перенесено в `discovery` executor; SQLite/legacy cache показывается сразу.
- Старт ускорен: `MainActivity` сначала показывает мгновенный shell, полный render уходит в следующий UI-pass.
- `YoruApp` больше не держит искусственные startup waits `350/700/12000/18000`.
- `ApiRepository` не читает `boot.json/counts.json` в `Application.onCreate()`; загрузка lazy/background.
- `SecureStore` не открывает AndroidKeyStore и не decrypt JSON в конструкторе; хранилище lazy.
- `Ui.text()` до готовности `SecureStore` не вызывает font/settings и не блокирует первый кадр.
- `TrafficMeter` не читает encrypted traffic-store на старте.
- Touch-анимации сокращены до 45/60 мс.
- `autoNextAllowed()` больше не режется через mobile/data-saver.

## Сохранено из 4.12.4

- `YoruCache.java`: SQLite fast-cache для `details`, `schedule`, `franchise`, `offline`, `progress` с TTL/лимитами/trim.
- `ApiRepository.quickDetails()` читает/пишет SQLite и unified identity cache по MAL/Anilist/KP/source key.
- Screen-cache для `Главная`/`Каталог`/`Коллекция`/`Календарь`.
- `DetailsActivity` использует `RecyclerView` для списка серий и отменяет старые detail/download задачи.
- `DownloadHub` имеет fast offline-index, чтобы UI не сканировал `DownloadIndex` на каждую строку серии.
- Франшиза: SQLite-cache, stable sort, фильтры `Все/Сезоны/Фильмы/OVA/Спешлы`, кнопка `Смотреть по порядку франшизу`.
- Hidden discovery использует максимум маршрутов в рамках дедлайна, без раннего stop по количеству найденных вариантов.
- Плеер не режет качество/буфер под mobile/data-saver default и заранее pre-check следующей серии.
- `ImageLoader` использует расширенный image-pool; poster disk-cache ограничен.

## Проверки

- `git diff --check`: OK перед коммитом.
- Локальная Gradle-сборка в Arena невозможна из-за отсутствия `java/JAVA_HOME`.
- GitHub Actions `34047539871`: success, шаг `Build Java release APK` прошёл.
- `sha256sum -c apk-output/YORU-4.12.5-release.apk.sha256`: OK.

## Жёсткие правила будущего продолжения

- Работать только с Java Android `app.yoru.mobile`, пока пользователь явно не поменяет направление.
- Не возвращать Kotlin, Forge/toolchain, Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальная команда перевода/дубляжа; технический маршрут не должен называться озвучкой.
- Выбранная/дефолтная озвучка — предпочтение для скорости, но отсутствие этой озвучки не должно ломать playback/download.
- Будущие серии должны быть визуально непроигрываемыми и показывать дату выхода/уточнение, а не обычную обложку.
- Календарь избранного должен оставаться быстрым: не возвращать repeated JSON scans, repeated filter scans и последовательные сетевые details по каждому избранному.
- Cache должен оставаться bounded: TTL, лимиты, trim, без бесконечного stale-cache и без лишнего расхода интернета.
