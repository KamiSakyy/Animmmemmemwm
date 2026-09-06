# YORU — состояние проекта после 4.6.0

Текущая активная работа — основной anime-app YORU. Forge и toolchain-pack поставлены на паузу по просьбе пользователя.

## Что сделано в 4.6.0

- Убрана главная причина лишнего трафика: `ApiRepository.loadYoruEpisode()` больше не резолвит много вариантов серии заранее.
- Если включён строгий выбранный голос, серия получает только совпадающие варианты этого голоса, остальные не попадают в плеер.
- Поток открывается лениво: только при реальном выборе варианта в YORU Player.
- Fallback в `PlayerActivity` не перескакивает на другой голос, когда пользователь уже смотрит выбранный голос.
- Выбор голоса внутри плеера сохраняется как строгий текущий выбор.
- Добавлен скрытый резерв YORU Reserve через открытые Anix/Sekai зеркала `api-s.anixsekai.com` и `api.anixsekai.com`.
- В новом резерве пользовательским голосом считается только название `type`, а технический `source` не показывается как голос.
- Новый резерв поддерживает `/filter/{page}`, `/release/{id}`, `/episode/{releaseId}`, `/episode/{releaseId}/{typeId}`, `/episode/{releaseId}/{typeId}/{sourceId}`.
- Усилен `VideoResolver.kodik()` под страницы, где есть `videoId`, `urlParams` и `/ftor`; добавлен запасной JSON-вызов.
- Автопоиск источников получил раннюю остановку после достаточного числа рабочих вариантов, чтобы новые резервы не тормозили основной слой.
- Android-версия поднята до `versionName 4.6.0`, `versionCode 37`.

## Ключевые файлы

- `yoru-android/app/src/main/java/app/yoru/mobile/ApiRepository.java` — YORU Source, Anix/Sekai резерв, нормализация голосов, строгая экономия трафика.
- `yoru-android/app/src/main/java/app/yoru/mobile/PlayerActivity.java` — нативный плеер, выбор голоса, fallback внутри текущего голоса.
- `yoru-android/app/src/main/java/app/yoru/mobile/VideoResolver.java` — HLS extraction и усиленный `/ftor` flow.
- `yoru-android/app/src/main/java/app/yoru/mobile/SourceEngine.java` — порядок маршрутов, ранжирование, статистика стабильности.
- `.github/workflows/build-apk.yml` — release-only APK build для `YORU-4.6.0-release.apk`.

## Проверки перед выпуском

В контейнере Arena сейчас нет локального `java`, поэтому Gradle-компиляция должна идти через GitHub Actions. Локально уже можно выполнять Java parse:

```bash
/tmp/yoru-check-venv/bin/python - <<'PY'
import pathlib, javalang
for p in pathlib.Path('yoru-android/app/src/main/java').rglob('*.java'):
    javalang.parse.parse(p.read_text())
print('JAVA_PARSE_OK')
PY

git diff --check
```

## Правила, которые нельзя ломать

- Говорить с пользователем на русском и показывать этапы/проценты.
- Не возвращать Forge/toolchain, пока пользователь явно не попросит.
- Не раскрывать пользователю технические маршруты как голоса.
- Не добавлять VPN, VLESS, proxy, приватные токены/API-ключи и MP4-конвертацию.
- Не тратить время на маркетинговые UI-заглушки: только стабильность, скорость, трафик, плеер, загрузки и рабочие источники.
