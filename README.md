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
SHA256 APK будет указан после CI-сборки.
Размер APK будет указан после CI-сборки.
Source handoff YORU: `handoff/YORU-4.15.0-source-handoff.zip`.
Инструкция будет сохранена как `handoff/YORU_4.15.0_BUILD.ru.md`.
CI run будет указан после сборки.

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
