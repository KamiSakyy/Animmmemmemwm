# YORU future agent guide

Работай только в текущей ветке Arena и не переключайся на другие ветки. Пользователь ждёт готовый APK после Android-правок, а не только исходники.

## Главные правила продукта

- Отвечай пользователю по-русски и показывай этапы/проценты, когда задача долгая.
- Код Java/Kotlin/Groovy/XML должен быть чистым, без комментариев в коде и без намёков на ИИ.
- Не добавляй источники, которые требуют API-ключей.
- Не добавляй прокси для аниме-источников.
- Не возвращай AniList, SameBand, AnimeGO и SovetRomantica.
- Сохраняй быстрый режим `Все источники`; сомнительные источники не должны блокировать общий каталог.
- Kodik 2026 оставлен как рабочий источник.
- Просмотр не должен ждать тяжёлых resolver-задач перед стартом iframe/плеера.
- Для YummyAnime iframe нужен неблокирующий перехват текущего воспроизводимого потока.
- Для скачанного HLS/DASH YORU автоматически собирает MP4 после завершения загрузки.
- Если внешний плеер не принимает `content://`, пользователь должен иметь действие сохранения через системный проводник Android.

## APK workflow

Основная сборка APK делается GitHub Actions workflow `.github/workflows/build-apk.yml`.

Обычный порядок:

1. Проверить `git status --short --branch`.
2. Внести Android-правки.
3. Запустить локальные smoke checks:
   - Java parse через `/tmp/javaparse/bin/python` и `javalang`, если venv уже есть.
   - XML parse через `xml.etree.ElementTree`.
   - `git diff --check`.
4. Поднять `versionName`, `versionCode`, workflow filenames, README/CHANGELOG.
5. Закоммитить исходники.
6. `git push origin arena/01a07147-animmmemmemwm`.
7. Ждать `gh run watch <run_id> --exit-status`.
8. После success сделать `git pull --ff-only origin arena/01a07147-animmmemmemwm`.
9. Проверить `sha256sum -c apk-output/YORU-<version>-*.sha256` и `unzip -t` для APK.
10. Открыть release APK через `present_file`.
11. В финальном ответе дать ссылки на repo commit, release APK, debug APK и Actions.

## VPN module notes

YORU 2.8.0 держит встроенный VPN экран, импорт подписок, выбор локаций, режим `только YORU / выбранные приложения / все приложения`, `VpnService` и Xray-core integration point. Критично: VLESS должен поддерживаться полноценно через Xray, включая Reality/TLS/WS/gRPC/H2/XHTTP/HTTPUpgrade/flow. Перед включением системного VPN подписка должна распаковываться через обычную не-VPN сеть Android. Вкладки `VPN` и `Загрузки` не должны падать из-за битых локальных записей. MP4/share/save/open должны отдавать только полный файл; экспорт проверяет `SimpleCache` spans и размер. VPN parser должен принимать plain/base64/base64url, JSON/sing-box/Xray, Clash JSON/YAML и ссылки внутри строк.

Чтобы не хранить огромный бинарный core в репозитории, workflow скачивает `libv2ray.aar` из MIT-проекта V2RayEZ перед сборкой APK. Код YORU обращается к `libv2ray` через reflection, поэтому исходники остаются собираемыми без локального AAR, а APK из Actions получает реальное ядро.

Не сохраняй пользовательские VPN subscription URLs в репозиторий, логи или README. Такие ссылки хранятся только в шифрованном `SecureStore` на устройстве.
