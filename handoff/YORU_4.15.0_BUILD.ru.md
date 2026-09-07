# YORU Android Java 4.15.0 — build / handoff

Дата: 2026-09-07
Пакет: `app.yoru.mobile`
Версия: `versionName 4.15.0`, `versionCode 57`

## Что исправлено

- Убран заголовок `Карточка+`: расширенный блок теперь называется `Подробнее`.
- Убраны пользовательские технические надписи про ответ каталога, скрытые механизмы, auto-подбор и порционную подгрузку.
- Настройки очищены от лишнего: нет ручной проверки избранного, длинного текста про фоновые проверки, мини-режима, режима `Не сохранять новую историю`, пункта `Данные и чистка`, импорта/экспорта/чистки коллекции в пользовательском меню.
- Каталог показывает локальные карточки/быстрые совпадения сразу, а сеть обновляет список без пустого экрана.
- Карточка тайтла запускает загрузку серий параллельно с полной карточкой, поэтому серии могут появиться раньше всех метаданных.
- Hidden playback pipeline ускорен без изменения пользовательского auto-mode: если уже найдена нужная озвучка или достаточно серий, YORU не ждёт все фоновые маршруты до общего таймаута.
- ImageLoader: убран `CopyOnWriteArrayList`, увеличен memory cache, заменена политика отказа задач, добавлен `inBitmap` reuse, включён `ARGB_8888`, убран принудительный `RGB_565` и искусственный downsample до 700px.
- Disk-cache изображений больше не пересжимает постеры в WebP 82%; URL-кандидаты сначала пробуют более качественные варианты.
- YoruCache: включён WAL через `setWriteAheadLoggingEnabled(true)`, trim перенесён в фон и ограничен примерно разом в час.
- YoruShield больше не читает SecureStore в конструкторе ApiRepository на main thread; профиль подтягивается асинхронно.
- Thread pools больше не используют `DiscardOldestPolicy`, foreground worker расширен.
- PlayerActivity сохраняет прогресс throttled примерно раз в 7 секунд + на важных событиях, prewarm следующей серии ограничен, WebView освобождается при native playback.
- TrafficMeter стал точнее и быстрее: стартовый sample без долгой паузы, восстановление прошлых счётчиков, обновление примерно каждые 1.5 секунды, persist вынесен из горячего UI-пути.
- Календарь стал cache-first: свежий кэш не запускает тяжёлое обновление при каждом открытии, а UI больше не раскрывает технические детали.

## Что сохранено

- Auto-mode остаётся по озвучкам, пользователь не выбирает скрытые источники.
- Новые UA Source Player источники не переносились в основной YORU.
- AllAnime/Anichi не добавлялись.
- Quality+ остаётся честным: 1440/2160 только при наличии реального потока.
- Коллекция, папки, загрузки, плеер, уведомления, Calendar и 4.13.0 Animetka playlist сохранены.

## APK

- APK: `apk-output/YORU-4.15.0-release.apk`
- SHA256: `2995537ebe8de10ce5e424cb9303ea59c93a44d89b608f53c369daff88331726`
- Размер: `2650604` bytes
- SHA-файл: `apk-output/YORU-4.15.0-release.apk.sha256`
- GitHub Actions run: `34129235538` — success
- Source commits: `e8893b4`, `69e09fc`, `2472c09`
- APK commit: `561e8f2` (`Add built YORU 4.15.0 release APK [skip ci]`)

## Проверка

- `sha256sum -c apk-output/YORU-4.15.0-release.apk.sha256` — OK.
- `unzip -t apk-output/YORU-4.15.0-release.apk` — No errors detected.
- `git diff --check` — OK перед source-handoff.
- Статический Java scan строк/скобок по `yoru-android/app/src/main/java/app/yoru/mobile/*.java` — OK.
- Локальная Gradle-сборка в Arena недоступна без Java/JAVA_HOME; релиз подтверждён GitHub Actions.

## Source handoff

- Source ZIP: `handoff/YORU-4.15.0-source-handoff.zip`
- Source ZIP SHA256: `7ae23ef63b95006d11f57da7fbb2e1b9a2ee72617564f5a851cdd61cae25b35e`
- Source ZIP размер: `1480708` bytes
- Source SHA-файл: `handoff/YORU-4.15.0-source-handoff.zip.sha256`

Source ZIP не включает сам build-handoff документ с этой SHA, чтобы не создавать self-referential checksum.
