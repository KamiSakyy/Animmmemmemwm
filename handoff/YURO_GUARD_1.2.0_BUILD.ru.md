# YURO Guard 1.2.0 — стабильный UI, Media Shield и DPI-lite

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Что это

YURO Guard — отдельное Android Java-приложение `app.yuro.guard`, не YORU. Исходники находятся в модуле `yoru-android/yuroguard`.

## APK

- Release APK: `apk-output/YURO-Guard-1.2.0-release.apk`
- SHA256: `0d062eceb579d9e1e865b034b08fb49a6c371ec953e873a0a20b6fc3dbcdc0d6`
- GitHub Actions run: `34053833877`
- Source commit: `710d507`
- APK commit: `df15ec1`
- Версия: `versionName 1.2.0`, `versionCode 3`

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

## Что исправлено и добавлено в 1.2.0

- Убрано мерцание/скачки главного экрана: таймер больше не пересоздаёт экран целиком каждые несколько секунд.
- Live-обновление теперь меняет только TextView метрик: всего, скорость, мобильный, Wi‑Fi, статус hero.
- Состояние scroll/list продолжает сохраняться, но не сбрасывается фоновым тикером.
- Добавлен `Media Shield / Только текст` в главном экране.
- Браузер в строгом режиме блокирует сетевые картинки, GIF, WebP/JPEG/PNG/SVG, видео, аудио, HLS/TS, веб-шрифты и трекеры.
- Браузер дополнительно смотрит `Accept`-заголовок запроса: image/video/audio запросы режутся даже без расширения в URL.
- Добавлен `PacketInspector`: DPI-lite парсер TUN-пакетов для DNS, HTTP Host/URL, TLS SNI, TCP/UDP направлений.
- Добавлен DNS-cache: DNS-ответы связывают IP с доменом для последующего сопоставления.
- Добавлен `/proc/net` сканер: периодически читает tcp/tcp6/udp/udp6 и сопоставляет соединения с UID/пакетом, если Android разрешает доступ.
- Добавлен журнал направлений в отчёте: приложение/UID → домен/IP:порт, протокол, источник события, media/CDN флаг.
- В SQLite добавлены таблицы `dns_cache` и `net_events`; журнал ограничен последними 5000 событиями, DNS-cache триммится по TTL.

## Важные Android-ограничения

- HTTPS `Content-Type` скрыт внутри TLS. Без установки пользовательского MITM-сертификата Android-приложение не может честно читать `Content-Type` чужого HTTPS-трафика.
- Поэтому системный VPN DPI-lite анализирует видимое без расшифровки: DNS, IP/порт, HTTP без TLS, TLS SNI, URL-расширения и домены/CDN.
- Встроенный браузер умеет реально блокировать медиа до сети, потому что WebView даёт перехват URL/headers.
- Android разрешает только один активный `VpnService` одновременно.
- Без root плавный QoS-throttle пакетов недоступен; лимиты реализованы no-root способом через VPN pause-shaper.
