# Animmmemmemwm

Текущие Android-продукты в ветке:

1. **YORU Android Java 4.12.7** (`app.yoru.mobile`) — основное anime-приложение.
2. **YURO Guard 1.0.0** (`app.yuro.guard`) — отдельное внеплановое приложение для мониторинга трафика, локального VPN-firewall, лимитов и встроенного браузера.

## YURO Guard 1.0.0

Исходники: `yoru-android/yuroguard`.
Workflow сборки: `.github/workflows/build-yuroguard-apk.yml`.

Готовый release APK: `apk-output/YURO-Guard-1.0.0-release.apk`.
SHA256 APK: `7e9ae8d4cefabc58c2b2e08c9976f0b509cbb8610b567592c30d2cbbf485c588`.
Инструкция: `handoff/YURO_GUARD_1.0.0_BUILD.ru.md`.

Ключ: локальный `VpnService` без внешнего сервера, режимы “только выбранные онлайн”, “блок выбранных”, лимит выбранных/всех через VPN pause-shaper, детальный SQLite-отчёт с запуска приложения, системные приложения по галочке, мобильная статистика через Usage Access/NetworkStatsManager, встроенный тёмный Chromium WebView-браузер с arena.ai и Яндекс-поиском.

## YORU Android Java 4.12.7

Исходники: `yoru-android/app`. Kotlin-модуль не участвует в активной сборке YORU; Gradle собирает `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK: `apk-output/YORU-4.12.7-release.apk`.
SHA256 APK текущего пересобранного файла: `8c6f03f105787293169b5127c1ab32c317894e8f4200a20c0f3bbb854516d8a9`.
Source handoff YORU: `handoff/YORU-4.12.7-source-handoff.zip`.

Ключ YORU 4.12.7: календарь переделан в один полноэкранный `RecyclerView` и по умолчанию показывает весь поток событий за 28 дней через прокрутку; искусственный выбор одного дня убран; видимые системные полосы прокрутки скрыты; главная снова с красивым anime hero/poster без технического текста; загрузки получили крупные preview/poster-карточки для скачанных эпизодов.
