# YURO Guard 1.3.0 — иконки, стабильный отчёт, VPN без самоподключения, DPI-профиль

Дата: 2026-09-06. Ветка: `arena/01a07147-animmmemmemwm`.

## Что это

YURO Guard — отдельное Android Java-приложение `app.yuro.guard`, не YORU. Исходники находятся в модуле `yoru-android/yuroguard`.

## APK

- Release APK: `apk-output/YURO-Guard-1.3.0-release.apk`
- SHA256: `4bc2e1b6858132203130a578a92a2877ce2878e1a4ff188b7aa2eef958af91ba`
- GitHub Actions run: `34054620380`
- Source commit: `2ee6c9f`
- APK commit: `0f2d3ac`
- Версия: `versionName 1.3.0`, `versionCode 4`

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

## Что исправлено и добавлено в 1.3.0

- Нижняя навигация стала icon-only: нарисованные YURO-иконки вместо текстовых кнопок.
- Добавлен класс `YuroIcon` с кастомными vector-like иконками: shield, apps, chart, browser, media, DPI, power.
- Исправлен баг сохранения scroll/tab: при смене вкладки scroll сохраняется в старую отрисованную вкладку, а не в новую.
- Экран отчёта обёрнут в защитный fallback: если Android API/данные дадут ошибку, YURO покажет восстановление отчёта и не перекинет в браузер.
- `VpnService.prepare()` больше не вызывается при входе в приложение или обычной отрисовке центра точности.
- Проверка VPN-разрешения теперь не трогает другой активный VPN.
- После boot/package update YURO Guard больше не поднимает свой VPN автоматически. Автостарт оставлен только для постоянного счётчика.
- Разделены сценарии: “только выдать VPN-разрешение” не подключает VPN; “Включить VPN/Подключить VPN” подключает только после явного нажатия.
- Добавлен отдельный блок `DPI обход` с иконкой, тумблером профиля и кнопкой подключения.
- DPI-профиль не запускает VPN сам: чужой VPN может отключиться только если пользователь явно нажмёт подключение YURO VPN и подтвердит системное окно Android.
- `PacketInspector` усилил DPI-lite события: QUIC UDP/443, TLS SNI, HTTP Host/URL получают отдельные DPI/event флаги.

## Важные Android-ограничения

- Android разрешает только один активный `VpnService` одновременно. Поэтому любой VPN-app, включая YURO Guard, может заменить другой VPN только после явного пользовательского подключения/подтверждения.
- HTTPS `Content-Type` скрыт внутри TLS. Без MITM-сертификата приложение не может читать тело/Content-Type чужого HTTPS.
- Реально доступный no-root DPI-lite без расшифровки: DNS, IP/порт, HTTP без TLS, TLS SNI, URL-расширения, CDN-домены, `/proc/net` при доступности Android.
- Полноценный GoodbyeDPI-style обход на Android требует forwarding/tun2socks/packet manipulation стека; в 1.3.0 добавлен отдельный DPI-профиль и журнал/кандидаты без автоподключения.
