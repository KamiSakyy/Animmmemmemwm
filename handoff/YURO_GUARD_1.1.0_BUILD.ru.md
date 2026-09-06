# YURO Guard 1.1.0 — профессиональное обновление мониторинга, VPN и браузера

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Что это

YURO Guard — отдельное Android Java-приложение `app.yuro.guard`, не YORU. Исходники находятся в модуле `yoru-android/yuroguard`.

## APK

- Release APK: `apk-output/YURO-Guard-1.1.0-release.apk`
- SHA256: `756bc013ac02c88c6696134e1b8ca817005327bd648d6a3db726a075b72aa554`
- GitHub Actions run: `34053148513`
- Source commit: `cb9a3db`
- APK commit: `381297e`
- Версия: `versionName 1.1.0`, `versionCode 2`

## Сборка

```bash
cd yoru-android
./gradlew --no-daemon :yuroguard:assembleRelease
```

Итоговый локальный файл:

```text
yoru-android/yuroguard/build/outputs/apk/release/yuroguard-release.apk
```

В Arena локального JDK может не быть, поэтому сборка делается через `.github/workflows/build-yuroguard-apk.yml`.

## Что усилилось в 1.1.0

- Добавлен отдельный foreground `MonitorService`: трафик считается всегда, даже когда VPN-firewall выключен.
- Мониторинг автоматически стартует при запуске приложения; добавлен `BootReceiver` для восстановления после перезагрузки/обновления.
- Счётчик разделён от VPN: VPN используется для блокировки/лимитов, статистика работает независимо.
- Добавлены специальные разрешения и центр точности: Usage Access, уведомления, исключение из экономии батареи, VPN-разрешение.
- SQLite обновлён до схемы 2: кроме `TrafficStats` добавлена таблица `net_samples` для точных Android `NetworkStatsManager` бакетов.
- При Usage Access приложение собирает точные мобильные и Wi‑Fi бакеты по UID; отчёт показывает Все сети / Мобильный точный / Wi‑Fi точный.
- Сохранение состояния главного интерфейса: текущая вкладка, поиск, период отчёта, источник отчёта, scroll position и позиция списка приложений.
- UI стал профессиональнее: live hero, отдельный постоянный мониторинг, центр точности, улучшенные карточки метрик.
- Браузер переписан в PRO-режим: несколько вкладок, быстрое переключение без перезагрузки уже созданных WebView, сохранение списка вкладок/URL/скролла.
- В браузере включён `LOAD_CACHE_ELSE_NETWORK`, cookies/DOM storage для входов, arena.ai и Яндекс в быстром доступе.
- Максимальная экономия трафика браузера: сетевые картинки, GIF, WebP/JPEG/PNG/SVG, видео, аудио, HLS/TS, веб-шрифты и известные трекеры блокируются.

## Android-ограничения

- Точный отчёт по мобильной сети/Wi‑Fi для всех UID требует ручной выдачи Usage Access в настройках Android.
- Android разрешает только один активный `VpnService` одновременно.
- Без root Android не даёт прямой плавный QoS-throttle каждого пакета; лимиты реализованы no-root способом через VPN pause-shaper при превышении заданной скорости.
