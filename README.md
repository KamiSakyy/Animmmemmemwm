# Animmmemmemwm

Текущий продукт: **YORU Android Java 4.12.7** (`app.yoru.mobile`).

Исходники находятся в `yoru-android/app`. Kotlin-модуль не участвует в активной сборке; Gradle собирает только `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK после CI: `apk-output/YORU-4.12.7-release.apk`.
SHA256 APK: `d898d9265998d503f67d4530420db91c4812f1a2a7dc3734f1c04e717e5231ec`.
Инструкция сборки: `handoff/BUILD_APK_GUIDE.ru.md`.
Source handoff: `handoff/YORU-4.12.7-source-handoff.zip`.

Ключ 4.12.7: срочный Java-hotfix после 4.12.6 — календарь переделан в один полноэкранный `RecyclerView` и по умолчанию показывает весь поток событий за 28 дней через прокрутку; искусственный выбор одного дня убран; видимые системные полосы прокрутки скрыты; главная снова с красивым anime hero/poster без технического текста; загрузки получили крупные preview/poster-карточки для скачанных эпизодов.
