# Animmmemmemwm

Текущий продукт: **YORU Android Java 4.12.4** (`app.yoru.mobile`).

Исходники находятся в `yoru-android/app`. Kotlin-модуль не участвует в активной сборке; Gradle собирает только `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK после CI: `apk-output/YORU-4.12.4-release.apk`.
SHA256 APK: `71d3d3bb92df4858ec62b2f5ed5867ac55c820701fa34ff0db3c721ff3dde24c`.
Инструкция сборки: `handoff/BUILD_APK_GUIDE.ru.md`.

Ключ 4.12.4: Java-only ускорение запуска/вкладок/календаря/деталей — RecyclerView для календаря и серий, screen-cache вкладок, SQLite fast-cache с TTL/лимитами, быстрый offline-index, silent refresh избранного календаря, franchise-cache/порядок/фильтры и максимальные hidden routes/threads без дефолтного урезания под слабые устройства.
