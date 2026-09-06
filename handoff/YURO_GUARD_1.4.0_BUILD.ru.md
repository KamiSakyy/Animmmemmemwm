# YURO Guard 1.4.0 — Telegram Rescue / Auto DPI

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Что это

YURO Guard — отдельное Android Java-приложение `app.yuro.guard`, не YORU. Исходники находятся в модуле `yoru-android/yuroguard`.

## APK

- Release APK: `apk-output/YURO-Guard-1.4.0-release.apk`
- SHA256: `7223276eb004bf6df857d2cc6d76082df21715b74096f7547bf4ad682753526e`
- GitHub Actions run: `34055621372`
- Source commit: `8c16849`
- APK commit: `955616a`
- Версия: `versionName 1.4.0`, `versionCode 5`

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

## Что добавлено в 1.4.0

- Добавлен `DpiProxyService`: локальный SOCKS5 Telegram Rescue proxy на `127.0.0.1:10808`.
- Proxy работает отдельно от Android VPN и не выключает другой VPN.
- Кнопка `Авто Telegram`: выбирает найденные пакеты Telegram/Nekogram, запускает SOCKS5 Rescue, включает DPI-профиль, открывает deep-link Telegram proxy.
- Кнопка `Telegram`: открывает `tg://socks?server=127.0.0.1&port=10808` или fallback `https://t.me/socks?...`.
- Кнопка `Автоподбор`: проверяет Telegram DC IP на TCP/443 и сама выбирает уровень агрессивности `dpiAutoLevel` 2/3/4.
- Proxy использует adaptive first-flight splitting: plain fallback, SNI split, delayed split, micro TCP split, aggressive first-flight split.
- Для TLS ClientHello ищется SNI offset и split ставится около SNI.
- Для MTProto/не-TLS первого payload используется пакетное дробление первых байтов, чтобы DPI видел не цельный fingerprint.
- Все соединения proxy пишутся в журнал: host/IP/port, стратегия, ошибки подключения, выбранный уровень.
- BootReceiver может поднять SOCKS5 proxy после перезагрузки только если пользователь ранее включил proxy; Android VPN по-прежнему не автоподнимается.
- DPI-блок в UI обновлён: Telegram Rescue, SOCKS5 ON/OFF, агрессивность 1–4, Авто Telegram, Автоподбор, YURO VPN DPI.

## Что важно понимать

- Для Telegram без отключения другого VPN лучше использовать SOCKS5 Rescue: нажать `Авто Telegram`, затем принять proxy в Telegram.
- Android VPN остаётся отдельной кнопкой. Если нажать `YURO VPN DPI` и подтвердить системное окно, Android может выключить другой VPN — это системное ограничение одного VpnService.
- Socket-level split реально дробит первый TCP payload через `TCP_NODELAY` и задержки. Raw-only техники вроде wrong checksum, low TTL fake packet, true TCP out-of-order/OOB без root/NDK/raw sockets недоступны честно на обычном Java Socket; поэтому они не отправляются как server-visible мусор, чтобы не ломать Telegram.
- ByeDPIAndroid архитектурно использует локальный VPN + SOCKS5/hev tunnel; RIPDPI делает adaptive per-target стратегии с Rust native engine. В 1.4.0 реализована Java no-root часть: локальный SOCKS5 + adaptive first-flight split + Telegram автоподбор + журнал.
