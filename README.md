# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.13.0** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.5.0** (`app.yuro.guard`) — отдельное приложение для мониторинга трафика, локального Android VPN-firewall, VPN-only DPI-lite профиля, рейтинга потребления и экономного WebView-браузера.

## YURO Guard 1.5.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.5.0-release.apk`.
SHA256 APK: `e04af0c6d573b40a4bffcaf70e45c2ffdc4c4a6baf2c996ba033545d8039b71f`.
Инструкция: `handoff/YURO_GUARD_1.5.0_BUILD.ru.md`.
Source handoff: `handoff/YURO-Guard-1.5.0-source-handoff.zip`.

Ключ 1.5.0: proxy/SOCKS5 полностью удалён. Telegram Rescue переведён на **YURO VPN-only**: внутренний TUN bridge для выбранных Telegram-приложений, DNS/UDP/TCP forwarding, QUIC UDP/443 block и adaptive TCP/TLS first-flight split в рамках Android `VpnService`. Главная и отчёт получили рейтинг “кто больше потребил”, топ-приложения, live totals/rates и более аккуратный YURO dark UI. Открытие приложения не запрашивает VPN и не отключает другой VPN; Android VPN permission/start вызывается только по кнопке подключения.

## YORU Android Java 4.13.0

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.13.0-release.apk`.
SHA256 APK: `c1514e276127a137d8a2bde55a46f50c202ae205e7ab60a2919efffcc74607ea`.
Размер APK: `2651088` bytes.
Source handoff YORU: `handoff/YORU-4.13.0-source-handoff.zip`.
Инструкция: `handoff/YORU_4.13.0_BUILD.ru.md`.
CI run: `34063163110`.

Ключ YORU 4.13.0: Quality+ добавляет честные 1440p/2160p/“лучшее доступное” только при наличии реального потока; любимые озвучки стали списком приоритета для карточки, плеера, источников и скачивания; Карточка+ показывает расширенные публичные метаданные; коллекция получила пользовательские папки/полки поверх старых статусов. Animetka подключена глубже через публичный `/api/anime/playlist`, без private tokens/auth bypass/DRM bypass. Оптимизации календаря 4.12.11, cold start/cache 4.12.10, bounded executors 4.12.9 и фоновые уведомления 4.12.8 сохранены.


## YORU Source Lab 0.1.0

Отдельное тестовое приложение `app.yoru.sourcelab` для проверки новых источников перед переносом в YORU. YORU 4.13.0 и его текущие источники не изменяются.

Категории Source Lab: AnimeON, Coani, AnimeUA, AllAnime, Anichi.

Исходники: `yoru-android/sourcelab`.
Workflow сборки: `.github/workflows/build-sourcelab-apk.yml`.
Готовый release APK: `apk-output/YORU-SourceLab-0.1.0-release.apk`.
SHA256 APK: `492a062849b81a3bdb7b4186bea9a1909e56abceb265d53e509478d1eee25823`.
Source handoff: `handoff/YORU-SourceLab-0.1.0-source-handoff.zip`.
Инструкция: `handoff/YORU_SOURCE_LAB_0.1.0_BUILD.ru.md`.
Backup YORU перед Source Lab: `handoff/YORU-4.13.0-before-source-lab.zip`.
