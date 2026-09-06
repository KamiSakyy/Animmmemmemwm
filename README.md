# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.12.7** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.0.0** (`app.yuro.guard`) — отдельное внеплановое приложение для мониторинга трафика, локального VPN-firewall, лимитов и встроенного браузера.

## YURO Guard 1.2.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.2.0-release.apk`.
SHA256 APK: `0d062eceb579d9e1e865b034b08fb49a6c371ec953e873a0a20b6fc3dbcdc0d6`.
Инструкция: `handoff/YURO_GUARD_1.2.0_BUILD.ru.md`.
Source handoff: `handoff/YURO-Guard-1.2.0-source-handoff.zip`.

Ключ 1.2.0: исправлены мерцание и скачки — таймер больше не пересоздаёт весь экран, обновляются только live-метрики. Добавлен Media Shield/только текст, строгий браузерный блок медиа по URL и `Accept`, DPI-lite парсер VPN TUN-пакетов: DNS, HTTP Host/URL, TLS SNI, TCP/UDP, DNS-cache, `/proc/net` сопоставление UID→IP/порт и журнал направлений.

## YORU Android Java 4.12.7

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.12.7-release.apk`.
SHA256 APK текущего пересобранного файла: `8c6f03f105787293169b5127c1ab32c317894e8f4200a20c0f3bbb854516d8a9`.
Source handoff YORU: `handoff/YORU-4.12.7-source-handoff.zip`.

Ключ YORU 4.12.7: календарь переделан в один полноэкранный `RecyclerView` и по умолчанию показывает весь поток событий за 28 дней через прокрутку; искусственный выбор одного дня убран; видимые системные полосы прокрутки скрыты; главная снова с красивым anime hero/poster без технического текста; загрузки получили крупные preview/poster-карточки для скачанных эпизодов.
