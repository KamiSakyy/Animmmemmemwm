# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.12.10** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.5.0** (`app.yuro.guard`) — отдельное приложение для мониторинга трафика, локального Android VPN-firewall, VPN-only DPI-lite профиля, рейтинга потребления и экономного WebView-браузера.

## YURO Guard 1.5.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.5.0-release.apk`.
SHA256 APK: `e04af0c6d573b40a4bffcaf70e45c2ffdc4c4a6baf2c996ba033545d8039b71f`.
Инструкция: `handoff/YURO_GUARD_1.5.0_BUILD.ru.md`.
Source handoff: `handoff/YURO-Guard-1.5.0-source-handoff.zip`.

Ключ 1.5.0: proxy/SOCKS5 полностью удалён. Telegram Rescue переведён на **YURO VPN-only**: внутренний TUN bridge для выбранных Telegram-приложений, DNS/UDP/TCP forwarding, QUIC UDP/443 block и adaptive TCP/TLS first-flight split в рамках Android `VpnService`. Главная и отчёт получили рейтинг “кто больше потребил”, топ-приложения, live totals/rates и более аккуратный YURO dark UI. Открытие приложения не запрашивает VPN и не отключает другой VPN; Android VPN permission/start вызывается только по кнопке подключения.

## YORU Android Java 4.12.10

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.12.10-release.apk`.
SHA256 APK: `64749e41e8e098cb8be64033a9fa32ec7bdf4d8b1ba9b9552b21e2b70a2aa434`.
Source handoff YORU: `handoff/YORU-4.12.10-source-handoff.zip`.

Ключ YORU 4.12.10: ускорен cold start и исправлены cache bottleneck'и — SecureStore preload в фоне, startup schedule/warmup без UI-блокировок, YoruCache без общего synchronized-монитора, throttled SQLite trim, cached todayScheduleCount, ConcurrentHashMap для HTTP/stream cache, SHA-256 cache keys и лимит raw memory cache 50KB. Улучшения 4.12.9 и фоновая схема уведомлений 4.12.8 сохранены.
