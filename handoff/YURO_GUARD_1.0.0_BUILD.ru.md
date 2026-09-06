# YURO Guard 1.0.0 — отдельное приложение мониторинга и VPN-firewall

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Что это

YURO Guard — отдельное Android Java-приложение `app.yuro.guard`, не YORU. Исходники находятся в модуле `yoru-android/yuroguard`.

## APK

- Release APK: `apk-output/YURO-Guard-1.0.0-release.apk`
- SHA256: `7e9ae8d4cefabc58c2b2e08c9976f0b509cbb8610b567592c30d2cbbf485c588`
- GitHub Actions run: `34051650646`
- Source commit: `75b6d56`
- APK commit: `719b3ac`
- Версия: `versionName 1.0.0`, `versionCode 1`

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

## Возможности 1.0.0

- Локальный `VpnService` без внешнего VPN-сервера.
- Режим `Только выбранные онлайн`: выбранные приложения исключаются из VPN и работают, все остальные уходят в локальный туннель и блокируются.
- Режим `Блок выбранных`: выбранные приложения отправляются в локальный VPN и блокируются, остальные работают напрямую.
- Режимы `Лимит выбранных` и `Лимит всем`: пакетный pause-shaper — приложение/группа получает сетевую паузу при превышении заданной скорости.
- Список приложений через `QUERY_ALL_PACKAGES`, отдельная галочка `Системные`.
- SQLite-отчёт с начала запуска YURO Guard: UID totals, текущая скорость, поминутные бакеты, часы и дни.
- Мобильный отчёт по UID через `NetworkStatsManager`, если пользователь выдаёт Usage Access.
- Встроенный тёмный Chromium WebView-браузер: arena.ai в рекомендациях, поиск через Яндекс, cookies/DOM storage для сохранения входа, режим экономии трафика.

## Важные Android-ограничения

- Без root Android не даёт системный API для плавного QoS-throttle каждого пакета. Поэтому лимит скорости реализован как рабочий VPN pause-shaper: при превышении окна скорости приложение/группа временно переводится в блокировку до следующего окна.
- Для точного мобильного отчёта по всем UID нужен Usage Access в настройках Android. Без него доступны общие счётчики `TrafficStats` и UID-статистика всех сетей.
- Android разрешает одновременно только один активный `VpnService` от пользователя.
