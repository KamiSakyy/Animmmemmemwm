# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.12.7** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.0.0** (`app.yuro.guard`) — отдельное внеплановое приложение для мониторинга трафика, локального VPN-firewall, лимитов и встроенного браузера.

## YURO Guard 1.4.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.4.0-release.apk`.
SHA256 APK: `7223276eb004bf6df857d2cc6d76082df21715b74096f7547bf4ad682753526e`.
Инструкция: `handoff/YURO_GUARD_1.4.0_BUILD.ru.md`.
Source handoff: `handoff/YURO-Guard-1.4.0-source-handoff.zip`.

Ключ 1.4.0: Telegram Rescue / Auto DPI. Добавлен локальный SOCKS5 proxy `127.0.0.1:10808`, который не отключает другой VPN, кнопка `Авто Telegram` открывает Telegram proxy deep-link, автоподбор проверяет Telegram DC и выбирает уровень aggressive split, proxy делает adaptive first-flight splitting/SNI split/micro split и пишет соединения/стратегии в журнал.

## YORU Android Java 4.12.7

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.12.7-release.apk`.
SHA256 APK текущего пересобранного файла: `8c6f03f105787293169b5127c1ab32c317894e8f4200a20c0f3bbb854516d8a9`.
Source handoff YORU: `handoff/YORU-4.12.7-source-handoff.zip`.

Ключ YORU 4.12.7: календарь переделан в один полноэкранный `RecyclerView` и по умолчанию показывает весь поток событий за 28 дней через прокрутку; искусственный выбор одного дня убран; видимые системные полосы прокрутки скрыты; главная снова с красивым anime hero/poster без технического текста; загрузки получили крупные preview/poster-карточки для скачанных эпизодов.
