# YORU — состояние проекта после 4.6.0

Текущая активная работа — основной anime-app YORU. Forge и toolchain-pack поставлены на паузу по просьбе пользователя.

## Что сделано в 4.6.0

- Убрана главная причина лишнего трафика: `ApiRepository.loadYoruEpisode()` больше не резолвит много вариантов серии заранее.
- Если включён строгий выбранная озвучка, серия получает только совпадающие варианты этого озвучки, остальные не попадают в плеер.
- Поток открывается лениво: только при реальном выборе варианта в YORU Player.
- Fallback в `PlayerActivity` не перескакивает на другой озвучка, когда пользователь уже смотрит выбранная озвучка.
- Выбор озвучки внутри плеера сохраняется как строгий текущий выбор.
- Добавлен скрытый резерв YORU Reserve через открытые Anix/Sekai зеркала `api-s.anixsekai.com` и `api.anixsekai.com`.
- В новом резерве пользовательским озвучкаом считается только название `type`, а технический `source` не показывается как озвучка.
- Новый резерв поддерживает `/filter/{page}`, `/release/{id}`, `/episode/{releaseId}`, `/episode/{releaseId}/{typeId}`, `/episode/{releaseId}/{typeId}/{sourceId}`.
- Усилен `VideoResolver.kodik()` под страницы, где есть `videoId`, `urlParams` и `/ftor`; добавлен запасной JSON-вызов.
- Автопоиск источников получил раннюю остановку после достаточного числа рабочих вариантов, чтобы новые резервы не тормозили основной слой.
- Android-версия поднята до `versionName 4.6.0`, `versionCode 37`.

## Ключевые файлы

- `yoru-android/app/src/main/java/app/yoru/mobile/ApiRepository.java` — YORU Source, Anix/Sekai резерв, нормализация озвучек, строгая экономия трафика.
- `yoru-android/app/src/main/java/app/yoru/mobile/PlayerActivity.java` — нативный плеер, выбор озвучки, fallback внутри текущей озвучки.
- `yoru-android/app/src/main/java/app/yoru/mobile/VideoResolver.java` — HLS extraction и усиленный `/ftor` flow.
- `yoru-android/app/src/main/java/app/yoru/mobile/SourceEngine.java` — порядок маршрутов, ранжирование, статистика стабильности.
- `.github/workflows/build-apk.yml` — release-only APK build для `YORU-4.6.0-release.apk`.

## Проверки перед выпуском

В контейнере Arena сейчас нет локального `java`, поэтому Gradle-компиляция должна идти через GitHub Actions. Локально уже можно выполнять Java parse:

```bash
/tmp/yoru-check-venv/bin/python - <<'PY'
import pathlib, javalang
for p in pathlib.Path('yoru-android/app/src/main/java').rglob('*.java'):
    javalang.parse.parse(p.read_text())
print('JAVA_PARSE_OK')
PY

git diff --check
```

## Правила, которые нельзя ломать

- Говорить с пользователем на русском и показывать этапы/проценты.
- Не возвращать Forge/toolchain, пока пользователь явно не попросит.
- Не раскрывать пользователю технические маршруты как озвучки.
- Не добавлять VPN, VLESS, proxy, приватные токены/API-ключи и MP4-конвертацию.
- Не тратить время на маркетинговые UI-заглушки: только стабильность, скорость, трафик, плеер, загрузки и рабочие источники.

## YORU 4.7.0

- Активная задача выполнена для основного anime-app, Forge не трогать без новой просьбы пользователя.
- `DetailsActivity.openWatch()` теперь открывает серию сразу, если выбран строгий озвучка или есть сохранённая озвучка этой серии.
- В `DetailsActivity` добавлены превью серий, блок кадров и трейлера.
- `Anime` теперь хранит `trailerUrl`, `screenshots`, а `Episode` хранит `poster`.
- `ApiRepository` подтягивает визуальные данные через Shikimori `/screenshots` и `/videos`, плюс берёт картинки из доступных полей источников.
- `PlayerActivity` вместо фразы `Устройтесь поудобнее` показывает следующие серии.
- Версия поднята до 4.7.0/38.

## YORU 4.8.0

- Короткая вертикальная лента полностью удалена из текущего приложения: файла activity, manifest-записи, UI-входа и helper-метода больше нет.
- Карточка аниме показывает все серии без лимита 80.
- Порядок карточки: описание, скриншоты, трейлер, просмотр, серии, связанные аниме, похожее.
- Скриншоты открываются через `ImageViewerActivity` с pinch-to-zoom и drag.
- `DetailsActivity` больше не содержит лишнее дополнительное меню.
- Настройки просмотра в карточке позволяют выбрать озвучку и 360p/480p/720p/1080p.
- `SecureStore.downloadResolution()` теперь допускает 360p.
- `ApiRepository.enrichRelated()` добирает связанные через Shikimori для разных источников и сортирует по дате/году выхода.
- `PlayerActivity` получил крупнее overlay-кнопки, мини-превью следующих серий и более заметную панель выбора.
- Версия поднята до 4.8.0/39.

## YORU 4.9.0

- `PlayerActivity` ускорен: выбранная озвучка может резолвиться в том же background-проходе, где загружается серия, без лишнего UI→IO цикла.
- `maybePrewarmNext()` теперь прогревает не только список следующей серии, но и выбранную озвучку/потоки, если сеть не в экономии.
- LoadControl YORU Player переведён на меньший стартовый буфер для быстрого начала просмотра.
- `MediaCache.http()` использует более короткие таймауты и допускает редиректы потоков.
- Видимая старая надпись подготовки удалена; добавлена glass-карточка подготовки видео.
- Overlay-кнопки плеера заменены на glass-стиль, play/pause и smart-fill стали акцентными.
- Добавлен seek-overlay `+10`/`−10` вместо toast для двойного тапа и кнопок перемотки.
- Добавлена кнопка `smartExpand()`: fullscreen + `RESIZE_MODE_ZOOM`, повторное нажатие возвращает `FIT`.
- Скорости расширены до `2.5×`.
- `ApiRepository` получил fallback Shikimori GraphQL/REST через `shikimori.io`, `shikimori.one`, `shikimori.me`.
- Kage/ReAnime/torrent-идеи изучены, но в 4.9.0 не добавлены как новые пользовательские источники, чтобы не тормозить YORU Source и не ломать правило реальных озвучек.
- Версия поднята до 4.9.0/40.

## YORU 4.10.0

- Основной Java YORU: loading overlay в `PlayerActivity` теперь только круглый `ProgressBar`, без длинных надписей.
- Добавлен отдельный модуль `yoru-android/kotlinapp` с `applicationId app.yoru.kotlin` и названием `YORU Kotlin`.
- Kotlin app построен на Compose Material 3 + Navigation + ViewModel + Coroutines + OkHttp + Coil + Media3/ExoPlayer; для тяжёлого стека включён multidex.
- Kotlin app реализует каталог/поиск/карточку/полный список серий/выбор качества/плеер/fullscreen/smart-fill через AniLibria API.
- `.github/workflows/build-apk.yml` теперь собирает `:app:assembleRelease` и `:kotlinapp:assembleRelease` и сохраняет два APK.
- Основной app поднят до 4.10.0/41; Kotlin app — 1.0.0-kotlin/2.
- Для сборки тяжёлого Kotlin/Compose APK Gradle heap увеличен до 4G, metaspace до 1G, workers=1; lint release у kotlinapp отключён.

## YORU Kotlin 1.1.0

- Пользователь подтвердил, что Kotlin-приложение быстрее и лучше запускает видео; дальнейшая активная работа теперь только в `yoru-android/kotlinapp`.
- Java YORU 4.10.0 остаётся доставленным APK, но новый основной фокус — Kotlin app.
- `MainActivity.kt` переписан под дизайн YORU: главный экран, каталог, коллекция, загрузки, календарь, настройки, карточка, fullscreen image viewer и player.
- Добавлены `YoruModels.kt`, `YoruStore.kt`, `YoruRepository.kt` для чистой Kotlin-архитектуры без Java-легаси.
- Kotlin store хранит избранное, историю, настройки, скачанные серии, экспорт/импорт совместимого формата.
- Kotlin repository сохраняет быстрый AniLibria path, OkHttp cache и локальное сохранение видео без лишних технических экранов.
- Kotlin player: Media3/ExoPlayer, быстрый буфер, progress, resume, auto-next, opening skip, fullscreen, PiP, smart-fill, speed до 2.5×, double tap seek.
- `.github/workflows/build-apk.yml` переведён на сборку только `:kotlinapp:assembleRelease`, итоговый файл `YORU-Kotlin-1.1.0-release.apk`.

## Финал YORU Kotlin 1.1.0

- Source commit: `7c5cad5` плюс APK commit `9351b9d`.
- GitHub Actions Android run: `34034083572`, status success.
- Release APK: `apk-output/YORU-Kotlin-1.1.0-release.apk`.
- APK SHA256: `1af9661d308bda25536e4e5fd6015b7a727dd6eb15a5c4aa6487eb1e1b04efd7`.
- Source ZIP: `handoff/YORU-Kotlin-1.1.0-source-handoff.zip`.
- Source ZIP SHA256: `54817cebb4d86c7e9ef09da7df87aeb27083d5f32765ed0009903ae8d2f9d60d`.
