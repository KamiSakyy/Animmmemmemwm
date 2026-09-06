# YORU 4.13.0 — Quality+ / любимые озвучки / Карточка+ / папки

Дата: 2026-09-07
Пакет: `app.yoru.mobile`
Версия: `versionName 4.13.0`, `versionCode 55`
Source commit: `134a062a9326268030532384691e9f3cd2854abf`
APK commit: `c722f7f` (`Add built YORU 4.13.0 release APK [skip ci]`)
GitHub Actions run: `34063163110` — success

## Готовые файлы

- APK: `apk-output/YORU-4.13.0-release.apk`
- SHA256: `c1514e276127a137d8a2bde55a46f50c202ae205e7ab60a2919efffcc74607ea`
- Размер APK: `2651088` bytes
- SHA-файл: `apk-output/YORU-4.13.0-release.apk.sha256`
- Source handoff: `handoff/YORU-4.13.0-source-handoff.zip`
- Source handoff SHA256: `b711de97b378442fcfcbe33e28449636442f3375cf8a6f71a5435e9d35553574`
- Source handoff размер: `1477758` bytes

## Что изменено

1. **Quality+** — единый слой `QualityPlus` для 360/480/720/1080/1440/2160 и “лучшее доступное” в плеере/карточке/скачивании. 1440/2160 показываются только как фактический выбор потока, без апскейла и без фейкового “4K”.
2. **Любимые озвучки** — `SecureStore` хранит ordered `favoriteVoices`; выбранные дорожки добавляются в приоритет, `SourceEngine`, `PlayerActivity` и скачивание сортируют варианты по ним. Строгий режим “только выбранная” сохранён отдельно.
3. **Карточка+** — `Anime` получил расширенные поля (`countries`, `cast`, `crew`, `ratings`, `franchise`, `durationMinutes`, `translationsCount`, `shikimoriOrder`), а `DetailsActivity` показывает богатый блок контекста.
4. **Папки/полки** — `SecureStore` сохраняет `folders` у избранного и `libraryFolders`; `Ui.bucketDialog` получил управление папками, `MainActivity` фильтрует коллекцию по `folder:<name>`. Старые buckets (`В планах`, `Смотрю`, `Просмотрено`, `Отложено`, `Брошено`) сохранены.
5. **Animetka public playlist** — `ApiRepository` создаёт варианты `/api/anime/playlist?material=…&tid=…&episode=…`, `VideoResolver` парсит публичный `file` manifest и отдаёт реальные HLS/MP4/DASH качества.
6. **Общий каталог** — Animetka добавлена в `REAL_SOURCES`; top/search пагинация не выключена.
7. **Сохранение 4.12.x** — фоновые уведомления, performance/cache исправления, Calendar `Все` с paging по 5, отсутствие startup shell, отсутствие badge-цифр на нижней навигации и bounded cache сохранены.

## Безопасность источников

- Использованы только публичные browser-visible endpoints Animetka (`/api/anime/top`, `/api/anime/search`, `/api/anime/{id}`, `/api/anime/playlist`).
- Никаких private tokens, auth bypass, DRM bypass, Cloudflare/protection bypass или закрытых API не добавлено.
- 4K/2K включаются только если URL реально присутствует в ответе источника. Проверенная публичная Animetka playlist для material `21` отдавала только 720/480/360, поэтому YORU не обещает 4K там, где его нет.

## Проверка

- Локальная Java в Arena отсутствует: `cd yoru-android && ./gradlew --version` сообщает `JAVA_HOME is not set and no 'java' command could be found in your PATH`.
- Выполнено: `git diff --check`.
- Выполнена статическая проверка баланса строк/скобок Python-скриптом по Java-файлам.
- Первый CI после внедрения поймал illegal escape в Animetka regex; исправлено коммитом `134a062`.
- Финальный GitHub Actions run `34063163110` успешно прошёл шаги checkout/setup Java/setup Android/SDK/signing/build/save/upload.
- `sha256sum -c apk-output/YORU-4.13.0-release.apk.sha256` — OK.
- `unzip -t apk-output/YORU-4.13.0-release.apk` — No errors detected.

## Source handoff проверка

- `sha256sum handoff/YORU-4.13.0-source-handoff.zip` — `b711de97b378442fcfcbe33e28449636442f3375cf8a6f71a5435e9d35553574`.
- `unzip -t handoff/YORU-4.13.0-source-handoff.zip` — No errors detected.
