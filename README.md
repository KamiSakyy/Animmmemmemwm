# YORU 4.19.1 — исправления календаря и карточек загрузок

- Календарь открывается в «Все / Все дни», без ограничения первыми пятью событиями.
- Общий календарь не заменяется избранным при ошибке основного запроса: REST fallback работает до добавления личных событий. При недоступности общих данных есть предупреждение о неполном результате.
- Будущее скачивание теперь показано отдельной обычной карточкой серии в общем списке загрузок: постер, серия, озвучка, качество, дата/статус и отмена. Большая кнопка удалена.
- Кэш, нативный плеер и правила автоматических скачиваний сохранены.

[Release APK](apk-output/YORU-4.19.1-release.apk) · [Debug APK](apk-output/YORU-4.19.1-debug.apk) · [Исходники ZIP](handoff/YORU-4.19.1-source.zip) · [Сборка и ограничения](handoff/YORU-4.19.1-BUILD.ru.md)

---

# YORU 4.19.0

Обновление от `handoff/YORU-4.17.2-stable-source.zip`, versionCode 69.

- Только нативный видеоплеер YORU; кэш не менялся, RAM-кэш не добавлялся.
- Безопасная перегрузка очередей, отмена устаревших задач, reusable карточки и частичные обновления.
- Скачивание будущих серий: кнопка у будущей серии или события календаря → озвучка и качество → «Запланировать».
- Управление планами: **Загрузки → Будущие скачивания**. Уже переданные загрузки управляются обычными кнопками загрузок.
- Фоновая проверка примерно раз в 15 минут при разрешении Android; Doze/сеть/энергосбережение могут задерживать выполнение. После force-stop нужно открыть приложение снова.

Исходники: `handoff/YORU-4.19.0-source.zip` и SHA-256 рядом. Инструкция и ограничения: `handoff/YORU-4.19.0-BUILD.ru.md`.
Сборка: `.github/workflows/build-apk.yml`. APK: `apk-output/YORU-4.19.0-release.apk`, `apk-output/YORU-4.19.0-debug.apk`.

## Предыдущая документация других продуктов и выпусков

# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.15.0** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.5.0** (`app.yuro.guard`) — отдельное приложение для мониторинга трафика, локального Android VPN-firewall, VPN-only DPI-lite профиля, рейтинга потребления и экономного WebView-браузера.

## YURO Guard 1.5.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.5.0-release.apk`.
SHA256 APK: `e04af0c6d573b40a4bffcaf70e45c2ffdc4c4a6baf2c996ba033545d8039b71f`.
Инструкция: `handoff/YURO_GUARD_1.5.0_BUILD.ru.md`.
Source handoff: `handoff/YURO-Guard-1.5.0-source-handoff.zip`.

Ключ 1.5.0: proxy/SOCKS5 полностью удалён. Telegram Rescue переведён на **YURO VPN-only**: внутренний TUN bridge для выбранных Telegram-приложений, DNS/UDP/TCP forwarding, QUIC UDP/443 block и adaptive TCP/TLS first-flight split в рамках Android `VpnService`. Главная и отчёт получили рейтинг “кто больше потребил”, топ-приложения, live totals/rates и более аккуратный YURO dark UI. Открытие приложения не запрашивает VPN и не отключает другой VPN; Android VPN permission/start вызывается только по кнопке подключения.

## YORU Android Java 4.15.0

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.15.0-release.apk`.
SHA256 APK: `2995537ebe8de10ce5e424cb9303ea59c93a44d89b608f53c369daff88331726`.
Размер APK: `2650604` bytes.
Source handoff YORU: `handoff/YORU-4.15.0-source-handoff.zip`.
Инструкция: `handoff/YORU_4.15.0_BUILD.ru.md`.
CI run: `34129235538`.

Ключ YORU 4.15.0: ускорение каталога, карточек, календаря, плеера, изображений, SQLite и счётчика трафика без изменения auto-mode; убраны лишние технические тексты/настройки; 4.14.0 также сохранён: auto-mode по озвучкам сохранён, технические лейблы убраны из пользовательского UI, карточки/главная стали чище и быстрее, добавлены прогресс на карточке и “Новые серии для вас”; 4.13.0 также сохранён: Quality+ добавляет честные 1440p/2160p/“лучшее доступное” только при наличии реального потока; любимые озвучки стали списком приоритета для карточки, плеера, скрытого просмотра и скачивания; расширенная карточка показывает публичные метаданные; коллекция получила пользовательские папки/полки поверх старых статусов. Animetka подключена глубже через публичный `/api/anime/playlist`, без private tokens/auth bypass/DRM bypass. Оптимизации календаря 4.12.11, cold start/cache 4.12.10, bounded executors 4.12.9 и фоновые уведомления 4.12.8 сохранены.

## YORU Source Player 0.2.0 — отложенный тестовый прототип

Отдельное тестовое anime-приложение `app.yoru.sourcelab` для просмотра и проверки новых источников перед переносом в YORU. YORU 4.13.0 и его текущие источники не изменяются.

Категории Source Player: AnimeON, Coani, AnimeUA. AllAnime и Anichi удалены как нерабочие/неподтверждённые.

Возможности: каталог, карточки, серии/варианты, поиск прямого HLS/MP4 и собственный native video player на Media3 ExoPlayer.

Исходники: `yoru-android/sourcelab`.
Workflow сборки: `.github/workflows/build-sourcelab-apk.yml`.
Готовый release APK: `apk-output/YORU-SourcePlayer-0.2.0-release.apk`.
SHA256 APK: `72bfe6851cc0877a9d8ca5cb31b774aceb3ff301bf3289c6c26bc411079afc19`.
Source handoff: `handoff/YORU-SourcePlayer-0.2.0-source-handoff.zip`.
Инструкция: `handoff/YORU_SOURCE_PLAYER_0.2.0_BUILD.ru.md`.
Backup YORU перед Source Player: `handoff/YORU-4.13.0-before-source-lab.zip`.
