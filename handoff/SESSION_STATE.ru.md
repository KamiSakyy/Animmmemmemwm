# YORU — состояние проекта после исправления 4.5.2

Текущая задача: исправить ошибку 4.5.0, где внутренние маршруты попали в список озвучек. В 4.5.2 маршруты не считаются голосами, список озвучек строится по реальным названиям конкретной серии, а выбор голоса доступен перед просмотром.

Ключевые файлы:

- `yoru-android/app/src/main/java/app/yoru/mobile/ApiRepository.java` — агрегация, нормализация голосов, быстрые варианты скачивания.
- `yoru-android/app/src/main/java/app/yoru/mobile/PlayerActivity.java` — один YORU Player, группировка вариантов по озвучкам.
- `yoru-android/app/src/main/java/app/yoru/mobile/DownloadActions.java` — выбор доступных озвучек конкретной серии перед скачиванием.
- `yoru-android/app/src/main/java/app/yoru/mobile/SecureStore.java` — настройка выбранной озвучки и strict-фильтра.
- `.github/workflows/build-apk.yml` — release-only APK build.

Не раскрывать пользователю внутренние детали маршрутов. В чате объяснять изменения простыми словами на русском.
