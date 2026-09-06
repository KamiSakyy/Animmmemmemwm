# YURO Guard 1.5.0 — финальный крупный VPN-only update

## Итог

- Приложение: **YURO Guard**
- Package/Application ID: `app.yuro.guard`
- Версия: `1.5.0`
- VersionCode: `6`
- Release APK: `apk-output/YURO-Guard-1.5.0-release.apk`
- SHA256 APK: `e04af0c6d573b40a4bffcaf70e45c2ffdc4c4a6baf2c996ba033545d8039b71f`
- GitHub Actions run: `34056596251` — success
- SHA256 source ZIP: `e83a9ea917191a2c624913af8b0a83d6f2e807fd73b6b7b219db60dbff7642d9`
- APK commit из CI: `8a827ad Add built YURO Guard 1.5.0 release APK [skip ci]`
- Основные source commits: `00c43f8`, `6ecc0cd`, `e0563b2`

## Что сделано в 1.5.0

1. **Proxy полностью удалён**
   - Удалён `DpiProxyService.java`.
   - Удалены manifest-service, boot autostart proxy, proxy prefs, SOCKS5/Telegram deep-link UI и все crash-prone proxy references.
   - Больше нет `127.0.0.1:10808`, SOCKS5 и `tg://socks`.

2. **Telegram/DPI только через YURO VPN**
   - Добавлен внутренний `TunBridge` для Android `VpnService`.
   - Режим `Telegram VPN Rescue` выбирает Telegram-пакеты и поднимает VPN-only bridge.
   - Реализованы DNS/UDP/TCP forwarding, `VpnService.protect(...)`, TCP handshake/forwarding и adaptive first-flight split для первых TCP/TLS данных.
   - Добавлен блок QUIC UDP/443, чтобы Telegram/HTTP3 чаще уходил в TCP-путь, где возможен split.
   - В журнал пишутся DNS/UDP/TCP/DPI события, IP/port/host где сигнал виден.

3. **Безопасное поведение с VPN**
   - Открытие YURO Guard не вызывает `VpnService.prepare()` и не отключает другой VPN.
   - VPN permission/start вызывается только по кнопке подключения.
   - BootReceiver восстанавливает только foreground monitor, а не VPN/proxy.

4. **Рейтинг потребления трафика**
   - На главной добавлен блок “Кто потребил больше всего”.
   - В отчёте приложения отсортированы по расходу, показываются `#1`, `#2`, проценты/полосы и live totals/rates.
   - Список приложений также показывает ранги по текущему потреблению.

5. **Дизайн и стабильность**
   - UI оставлен в YURO dark neon стиле, добавлены более наглядные карточки рейтинга.
   - Отчёт остаётся внутри приложения и не открывает браузер самопроизвольно.
   - Экспериментальный proxy-код, который мог крашить приложение, удалён.

## Важные честные ограничения

- Это no-root Android app. Без root/native raw packets нельзя честно обещать low-TTL fake packets, wrong checksum injection, настоящий raw disorder/out-of-order и похожие desktop GoodbyeDPI техники.
- HTTPS payload и `Content-Type` внутри TLS не видны без MITM-сертификата. YURO Guard не ставит MITM CA и не расшифровывает личный HTTPS-трафик.
- Telegram/DPI режим — это **попытка обхода/стабилизации через Android VPN-only bridge**, а не гарантия разблокировки у всех операторов.
- Android допускает только один активный `VpnService`. YURO Guard не трогает другой VPN при открытии приложения, но если пользователь явно нажмёт подключить YURO VPN, Android может отключить предыдущий VPN.

## Как пользоваться

1. Установить APK `YURO-Guard-1.5.0-release.apk`.
2. Открыть YURO Guard — приложение само не запускает VPN.
3. Для точного рейтинга трафика выдать Usage Access/NetworkStats permission из центра разрешений.
4. Для постоянного подсчёта оставить foreground monitor включённым.
5. Для Telegram/DPI нажать **“Авто Telegram VPN”** или **“Подключить VPN DPI”** и подтвердить Android VPN permission.
6. Если Telegram не установлен стандартным package name, выбрать нужное приложение вручную во вкладке приложений.

## Проверка сборки

- CI: GitHub Actions `Build YURO Guard APK`, run `34056596251`, статус success.
- APK SHA256 проверен локально: `e04af0c6d573b40a4bffcaf70e45c2ffdc4c4a6baf2c996ba033545d8039b71f`.
- `git diff --check` проходил перед source-коммитами.
