# YORU Source Lab 0.1.0 — отдельное тестовое приложение новых источников

Дата: 2026-09-07
Пакет: `app.yoru.sourcelab`
Версия: `versionName 0.1.0`, `versionCode 1`
Назначение: лёгкая отдельная APK для проверки новых источников перед решением, что можно переносить в YORU.

## Важно

- YORU 4.13.0 не изменён.
- Текущие источники YORU не добавлены в Source Lab.
- Source Lab содержит только новые категории:
  - AnimeON / `animeon.club`
  - Coani / `coani.net`
  - AnimeUA / `animeua.club`
  - AllAnime / `api.allanime.day` / `allanime.to`
  - Anichi / Cloudstream Anichi extension audit
- Перед созданием Source Lab сохранён backup текущего исходника YORU 4.13.0:
  - `handoff/YORU-4.13.0-before-source-lab.zip`
  - SHA256 `201e53d999dbd069b9e49f3dd05b60906b106b26d408bdc94f2da23e58e1d16b`

## Что делает приложение

- Показывает каждый новый источник отдельной категорией.
- Кнопка `Проверить` выполняет live-probe выбранного источника.
- Кнопка `Все источники` последовательно проверяет все категории.
- Для каждого источника показывает, какие звенья реально отвечают: catalog/search/details/episodes/video markers/HLS качества.
- Не использует private tokens, auth bypass, DRM/protection bypass и guessed private endpoints.

## Реализованные проверки

### AnimeON

Проверяются публичные API:

- `/api/anime?pageSize=5&pageIndex=1`
- `/api/anime?search=...`
- `/api/anime/{slug}`
- `/api/player/{animeId}/translations`
- `/api/player/{animeId}/episodes?...`
- `/api/player/{episodeId}/episode`

### Coani

Проверяются публичные API:

- `/api/public/film/catalog?search=...`
- `/api/public/film/season?slug=...`
- `/api/public/film/season/{id}/series`
- прямые `master.m3u8` из поля `video`, с чтением HLS master и качеств.

### AnimeUA

Проверяются public HTML/search:

- главная страница;
- `POST /index.php` с `do=search&subaction=search&story=...`;
- detail page;
- iframe/video/HLS markers в HTML.

Сайт сам предупреждает, что плеер может быть доступен только с территории Украины или через UA VPN; Source Lab это отображает как диагностическое ограничение.

### AllAnime

Проверяется public GraphQL POST:

- search;
- `availableEpisodesDetail`;
- `sourceUrls`;
- публичная расшифровка sourceUrl формата hex XOR `56`, описанная в open-source extension.

Hoster extractors не копируются в Source Lab — приложение только выявляет, что API отдаёт source URLs.

### Anichi

Категория добавлена честно как audit:

- найденное Cloudstream extension хранит `ANICHI_API`, `ANICHI_SERVER`, `ANICHI_ENDPOINT`, `ANICHI_APP` в `local.properties`, которых нет в публичном репозитории;
- Source Lab не угадывает и не использует private endpoints;
- выполняется только публичный meta-probe через Jikan, а вердикт показывает, что Anichi пока нельзя считать подтверждённым рабочим video-source без публичной API-цепочки.

## Сборка

Отдельный workflow: `.github/workflows/build-sourcelab-apk.yml`.

Модуль `yoru-android/sourcelab` намеренно не подключён постоянно в `yoru-android/settings.gradle`, чтобы не запускать YORU/YURO workflows и не менять основную сборку. Source Lab workflow временно добавляет `include ':sourcelab'` только в CI job.

Ожидаемый APK после CI:

- `apk-output/YORU-SourceLab-0.1.0-release.apk`
- `apk-output/YORU-SourceLab-0.1.0-release.apk.sha256`
