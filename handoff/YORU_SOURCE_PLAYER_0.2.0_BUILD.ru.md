# YORU Source Player 0.2.0 — отдельное приложение просмотра новых источников

Дата: 2026-09-07
Пакет: `app.yoru.sourcelab`
Версия: `versionName 0.2.0`, `versionCode 2`
Назначение: отдельная лёгкая APK для реального просмотра/проверки новых источников перед переносом чего-либо в YORU.

## Важно

- YORU 4.13.0 не изменяется и не использует код Source Player.
- Текущие источники YORU не трогаются.
- AllAnime и Anichi удалены из тестового приложения как нерабочие/неподтверждённые.
- В Source Player оставлены только новые источники:
  - AnimeON / `animeon.club`
  - Coani / `coani.net`
  - AnimeUA / `animeua.club`
- Перед созданием отдельного приложения сохранён backup YORU 4.13.0:
  - `handoff/YORU-4.13.0-before-source-lab.zip`
  - SHA256 `201e53d999dbd069b9e49f3dd05b60906b106b26d408bdc94f2da23e58e1d16b`

## Что теперь делает приложение

- Показывает настоящий каталог по категориям источников.
- Показывает карточки тайтлов с постером, годом, статусом, описанием.
- Открывает страницу тайтла внутри приложения.
- Показывает серии/варианты озвучки, если источник их отдаёт.
- По нажатию серии ищет прямой HLS/MP4 поток.
- Запускает собственный native video player на Media3 ExoPlayer внутри приложения.

## Источники

### AnimeON

- catalog/search: `/api/anime`.
- details: `/api/anime/{slug}`.
- translations: `/api/player/{animeId}/translations`.
- episodes: `/api/player/{animeId}/episodes?...`.
- videoUrl: `/api/player/{episodeId}/episode` или `/api/player/{playerId}/{translationId}`.
- Для Ashdi player добавлен публичный HTML resolver: ищет `file:'...m3u8'`/`.m3u8`/`.mp4` в player page и script tags.

### Coani

- catalog/search: `/api/public/film/catalog`.
- details: `/api/public/film/season?slug=...`.
- series: `/api/public/film/season/{id}/series`.
- Прямой HLS берётся из `data.video`, обычно `https://s*.coani.net/hls/.../master.m3u8`.
- Это самый прямой кандидат: каталог + карточка + серии + native player без iframe.

### AnimeUA

- catalog/search/details через публичный HTML и `POST /index.php`.
- Ищет `iframe`, `video`, `source`, `.m3u8`, `.mp4` markers.
- Сайт сам предупреждает о региональности плеера; если прямой поток не найден, приложение честно показывает ошибку, а не подделывает просмотр.

## Сборка

Отдельный workflow: `.github/workflows/build-sourcelab-apk.yml`.

Модуль `yoru-android/sourcelab` намеренно не подключён постоянно в `yoru-android/settings.gradle`. Workflow временно добавляет `include ':sourcelab'` только в CI job.

Ожидаемые файлы после CI:

- `apk-output/YORU-SourcePlayer-0.2.0-release.apk`
- `apk-output/YORU-SourcePlayer-0.2.0-release.apk.sha256`
