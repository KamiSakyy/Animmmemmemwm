# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.12.9** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.5.0** (`app.yuro.guard`) — отдельное приложение для мониторинга трафика, локального Android VPN-firewall, VPN-only DPI-lite профиля, рейтинга потребления и экономного WebView-браузера.

## YURO Guard 1.5.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.5.0-release.apk`.
SHA256 APK: `e04af0c6d573b40a4bffcaf70e45c2ffdc4c4a6baf2c996ba033545d8039b71f`.
Инструкция: `handoff/YURO_GUARD_1.5.0_BUILD.ru.md`.
Source handoff: `handoff/YURO-Guard-1.5.0-source-handoff.zip`.

Ключ 1.5.0: proxy/SOCKS5 полностью удалён. Telegram Rescue переведён на **YURO VPN-only**: внутренний TUN bridge для выбранных Telegram-приложений, DNS/UDP/TCP forwarding, QUIC UDP/443 block и adaptive TCP/TLS first-flight split в рамках Android `VpnService`. Главная и отчёт получили рейтинг “кто больше потребил”, топ-приложения, live totals/rates и более аккуратный YURO dark UI. Открытие приложения не запрашивает VPN и не отключает другой VPN; Android VPN permission/start вызывается только по кнопке подключения.

## YORU Android Java 4.12.9

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.12.9-release.apk`.
SHA256 APK: `4f3e805ab38b2cbbbb90d28fbc86e5a8373f5664c2f0e5172d410440742f4875`.
Source handoff YORU: `handoff/YORU-4.12.9-source-handoff.zip`.

Ключ YORU 4.12.9: performance/stability pass по лагам — bounded executors, меньшие network/image timeout'ы, дедупликация загрузки постеров, отмена устаревших catalog/search задач, кэширование статистики YoruBrain и lifecycle cleanup. Фоновая схема уведомлений 4.12.8 сохранена: persisted JobScheduler, AlarmManager fallback, восстановление после boot/update и автономная проверка.
