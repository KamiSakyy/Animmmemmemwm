# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.12.7** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.5.0** (`app.yuro.guard`) — отдельное приложение для мониторинга трафика, локального Android VPN-firewall, VPN-only DPI-lite профиля, рейтинга потребления и экономного WebView-браузера.

## YURO Guard 1.5.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.5.0-release.apk`.
SHA256 APK: `e04af0c6d573b40a4bffcaf70e45c2ffdc4c4a6baf2c996ba033545d8039b71f`.
Инструкция: `handoff/YURO_GUARD_1.5.0_BUILD.ru.md`.
Source handoff: `handoff/YURO-Guard-1.5.0-source-handoff.zip`.

Ключ 1.5.0: proxy/SOCKS5 полностью удалён. Telegram Rescue переведён на **YURO VPN-only**: внутренний TUN bridge для выбранных Telegram-приложений, DNS/UDP/TCP forwarding, QUIC UDP/443 block и adaptive TCP/TLS first-flight split в рамках Android `VpnService`. Главная и отчёт получили рейтинг “кто больше потребил”, топ-приложения, live totals/rates и более аккуратный YURO dark UI. Открытие приложения не запрашивает VPN и не отключает другой VPN; Android VPN permission/start вызывается только по кнопке подключения.

## YORU Android Java 4.12.7

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.12.7-release.apk`.
SHA256 APK текущего пересобранного файла: `8c6f03f105787293169b5127c1ab32c317894e8f4200a20c0f3bbb854516d8a9`.
Source handoff YORU: `handoff/YORU-4.12.7-source-handoff.zip`.

Ключ YORU 4.12.7: календарь переделан в один полноэкранный `RecyclerView` и по умолчанию показывает весь поток событий за 28 дней через прокрутку; искусственный выбор одного дня убран; видимые системные полосы прокрутки скрыты; главная снова с красивым anime hero/poster без технического текста; загрузки получили крупные preview/poster-карточки для скачанных эпизодов.
