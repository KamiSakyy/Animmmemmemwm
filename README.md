# Animmmemmemwm

Текущий продукт: **YORU Android Java 4.12.5** (`app.yoru.mobile`).

Исходники находятся в `yoru-android/app`. Kotlin-модуль не участвует в активной сборке; Gradle собирает только `:app`. Forge/toolchain в этой линии не используется.

Готовый release APK после CI: `apk-output/YORU-4.12.5-release.apk`.
SHA256 APK: `970d31677e4973d952dd26f7e65cf03825dfde7d9d22c7c025ffa89f9a35932b`.
Инструкция сборки: `handoff/BUILD_APK_GUIDE.ru.md`.

Ключ 4.12.5: hotfix после 4.12.4 — календарь снова нормальный вертикальный scroll, события/аниме видны, `RecyclerView` не конфликтует с экраном; убраны искусственные startup-waits; старт теперь показывает мгновенный YORU shell, а тяжёлые store/boot/traffic задачи загружаются лениво/в фоне.
