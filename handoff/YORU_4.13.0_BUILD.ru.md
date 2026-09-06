# YORU 4.13.0 — Quality+ / любимые озвучки / Карточка+ / папки

Дата: 2026-09-07
Пакет: `app.yoru.mobile`
Версия: `versionName 4.13.0`, `versionCode 55`

## Что изменено

1. **Quality+** — единый слой `QualityPlus` для 360/480/720/1080/1440/2160 и “лучшее доступное” в скачивании. 1440/2160 показываются только как фактический выбор потока, без апскейла.
2. **Любимые озвучки** — `SecureStore` хранит `favoriteVoices`, выбранные дорожки добавляются в приоритет, `SourceEngine`, `PlayerActivity` и скачивание сортируют варианты по ним.
3. **Карточка+** — `Anime` получил расширенные поля (`countries`, `cast`, `crew`, `ratings`, `franchise`, `durationMinutes`, `translationsCount`, `shikimoriOrder`), а `DetailsActivity` показывает богатый блок контекста.
4. **Папки/полки** — `SecureStore` сохраняет `folders` у избранного и `libraryFolders`; `Ui.bucketDialog` получил управление папками, `MainActivity` фильтрует коллекцию по `folder:<name>`. Старые buckets сохранены.
5. **Animetka public playlist** — `ApiRepository` создаёт варианты `/api/anime/playlist?material=…&tid=…&episode=…`, `VideoResolver` парсит публичный `file` manifest и отдаёт реальные HLS/MP4/DASH качества.
6. **Общий каталог** — Animetka добавлена в `REAL_SOURCES`, top/search пагинация не выключена.

## Безопасность источников

- Использованы только публичные browser-visible endpoints Animetka (`/api/anime/top`, `/api/anime/search`, `/api/anime/{id}`, `/api/anime/playlist`).
- Никаких private tokens, auth bypass, DRM bypass, Cloudflare bypass или закрытых API не добавлено.
- 4K/2K включаются только если URL реально присутствует в ответе источника.

## Проверка

- Локальная Java в Arena отсутствует: `./gradlew --version` сообщает `JAVA_HOME is not set and no 'java' command could be found in your PATH`.
- Выполнена статическая проверка баланса строк/скобок Python-скриптом по Java-файлам.
- Финальная APK-сборка должна идти через `.github/workflows/build-apk.yml`.
