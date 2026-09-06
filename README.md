# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.12.7** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.0.0** (`app.yuro.guard`) — отдельное внеплановое приложение для мониторинга трафика, локального VPN-firewall, лимитов и встроенного браузера.

## YURO Guard 1.3.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.3.0-release.apk`.
SHA256 APK: `4bc2e1b6858132203130a578a92a2877ce2878e1a4ff188b7aa2eef958af91ba`.
Инструкция: `handoff/YURO_GUARD_1.3.0_BUILD.ru.md`.
Source handoff: `handoff/YURO-Guard-1.3.0-source-handoff.zip`.

Ключ 1.3.0: icon-only навигация с кастомными YURO-иконками, исправлен отчёт/защита от вылета и перехода в браузер, исправлено сохранение scroll/tab, YURO VPN больше не трогает другой VPN при входе и не автоподключается после boot/update, добавлен отдельный DPI-обход профиль с QUIC/SNI/HTTP/DNS событиями и ручной кнопкой подключения.

## YORU Android Java 4.12.7

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.12.7-release.apk`.
SHA256 APK текущего пересобранного файла: `8c6f03f105787293169b5127c1ab32c317894e8f4200a20c0f3bbb854516d8a9`.
Source handoff YORU: `handoff/YORU-4.12.7-source-handoff.zip`.

Ключ YORU 4.12.7: календарь переделан в один полноэкранный `RecyclerView` и по умолчанию показывает весь поток событий за 28 дней через прокрутку; искусственный выбор одного дня убран; видимые системные полосы прокрутки скрыты; главная снова с красивым anime hero/poster без технического текста; загрузки получили крупные preview/poster-карточки для скачанных эпизодов.
