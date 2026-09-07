# YORU Android Java 4.14.0 — build / handoff

Дата: 2026-09-07
Пакет: `app.yoru.mobile`
Версия: `versionName 4.14.0`, `versionCode 56`

## Что сделано

- Масштабный polish-update основного YORU без добавления новых сайтов и без переноса украинских Source Player источников.
- Auto-mode не менялся: логика остаётся voice-first, YORU ищет выбранную озвучку во всей скрытой видеобазе, а не заставляет пользователя выбирать сайт/сервер.
- Пользовательский UI очищен от технических лейблов места хранения: карточки, details и offline/export говорят про YORU, озвучки, дорожки, прогресс и Quality+.
- Карточки стали богаче без новых кнопок: бейдж показывает продолжение, оценку, коллекцию, онгоинг или вышедшие серии; добавлен компактный прогресс просмотра и чип любимых озвучек/авто.
- Главная стала чище: блок `Сейчас в YORU` заменил перегруженный быстрый старт, оставлена только основная кнопка продолжения просмотра при наличии истории.
- Добавлен блок `Новые серии для вас`: берёт коллекцию/историю и показывает тайтлы, где есть непосмотренные серии, без ручного выбора скрытой видеобазы.
- Карточка+ убрала техническую строку места хранения и подчёркивает студию, страну, премьеру, длительность, рейтинги, папки, любимые озвучки, Quality+ и авто-подбор по видеобазе YORU.
- Экспорт/детали скачанного видео больше не показывают внутренний каталог; вместо этого выводится озвучка или `авто-подбор YORU`.
- 4.13.0 сохранён: Quality+, любимые озвучки, папки, Animetka playlist, фоновые уведомления, Calendar `Все` по 5 и performance-правки не урезались.

## Что специально не менялось

- `ApiRepository.playback(...)`, `SourceEngine.playbackOrder(...)`, `VideoResolver` и auto-mode engine не изменены.
- Новые украинские источники из Source Player не добавлялись в основной YORU.
- AllAnime/Anichi не добавлялись.
- Нет новых лишних быстрых кнопок на карточках.
- Нет fake 4K: 1440/2160 остаются только для реального потока.

## APK

- APK: `apk-output/YORU-4.14.0-release.apk`
- SHA256: `7941b5d20f1f067beccaa4ad0be27fe2887b021a3e323c1192f13f2edac2d18e`
- Размер: `2651968` bytes
- SHA-файл: `apk-output/YORU-4.14.0-release.apk.sha256`
- GitHub Actions run: `34109617422` — success
- Source commit: `6b07250` (`Polish YORU voice-first UI 4.14.0`)
- APK commit: `04ddc2d` (`Add built YORU 4.14.0 release APK [skip ci]`)

## Проверка

- `sha256sum -c apk-output/YORU-4.14.0-release.apk.sha256` — OK.
- `unzip -t apk-output/YORU-4.14.0-release.apk` — No errors detected.
- `git diff --check` — OK перед source-handoff.
- Статический Java scan строк/скобок по `yoru-android/app/src/main/java/app/yoru/mobile/*.java` — OK.
- Локальная Gradle-сборка в Arena по-прежнему недоступна без Java/JAVA_HOME; релиз подтверждён GitHub Actions.

## Source handoff

- Source ZIP: `handoff/YORU-4.14.0-source-handoff.zip`
- Source ZIP SHA256: `8616af04c9f9abfa868174b96f25e3259c7c6b8fb77831017af03a4900ecbe42`
- Source ZIP размер: `1479703` bytes
- Source SHA-файл: `handoff/YORU-4.14.0-source-handoff.zip.sha256`

Source ZIP не включает сам build-handoff документ с этой SHA, чтобы не создавать self-referential checksum.
