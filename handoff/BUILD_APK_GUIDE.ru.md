# YORU Android Java 4.12.4 — сборка Release APK

Текущий продукт: стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не участвует в активной сборке. Forge/toolchain в этом релизе не используется. C++/NDK в 4.12.4 не добавлялись: ускорение сделано Java/Android-архитектурой.

## Локальная сборка

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Итоговый APK:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

В Arena-песочнице Java/JDK может отсутствовать. Если `java` не найден, используйте GitHub Actions на ветке `arena/01a07147-animmmemmemwm`: workflow собирает только `:app:assembleRelease` и сохраняет `apk-output/YORU-4.12.4-release.apk`.

## Проверенный release 4.12.4

- APK: `apk-output/YORU-4.12.4-release.apk`
- SHA256: `71d3d3bb92df4858ec62b2f5ed5867ac55c820701fa34ff0db3c721ff3dde24c`
- GitHub Actions run: `34046274405`
- Source commit: `a4e35e9`
- APK commit: `c641f3c`
- Версия приложения: `versionName 4.12.4`, `versionCode 47`

## Что изменилось в 4.12.4

- Убран стартовый fade-from-black и двойной первый `onResume` render.
- Тяжёлые проверки подлинности/WebView/update-check/protection refresh перенесены после первого кадра.
- Добавлен bounded SQLite fast-cache: details, schedule, franchise, offline mirror, progress mirror; есть TTL, лимиты и trim.
- Календарь переведён на `RecyclerView`, читает SQLite/legacy cache сразу и обновляет сеть тихо.
- Серии в карточке переведены на `RecyclerView`.
- `MainActivity` кэширует экраны главной/каталога/коллекции/календаря и отменяет старые UI/background задачи при уходе.
- Добавлен быстрый offline-index в `DownloadHub`, чтобы UI не сканировал DownloadManager на каждой строке.
- Добавлен prefetch quick-details по видимым карточкам и smart prefetch по связанным тайтлам.
- Франшиза получает SQLite-cache, стабильный порядок, фильтры сезоны/фильмы/OVA/спешлы и кнопку `Смотреть по порядку франшизу`.
- Скрытый discovery больше не обрывается по лимиту “нашли 3/5 — хватит”; используется максимум маршрутов в рамках дедлайна.
- Плеер больше не режет качество/буферы под mobile/data-saver по умолчанию; включён pre-check streams следующей серии.
- Постеры грузятся отдельным расширенным image-pool без блокировки UI, disk-cache остаётся ограниченным.

## Важные правила продукта

- Активная база — Java YORU 4.12.4, `versionCode 47`.
- Kotlin не возвращать без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальные команды перевода/дубляжа. YORU/Yummy/Kodik/AnixSekai/внутренние маршруты — не озвучки.
- Выбранная озвучка — предпочтение и быстрый первый путь; если голоса нет, YORU обязан скрыто найти доступные озвучки/маршруты.
- Для онгоингов показывать `Вышло X из Y`, статус и индивидуальный `nextEpisodeAt`, когда источник отдаёт точную дату.
- Future-серии не должны выглядеть playable: без обычной обложки, play и download.
- Календарь `Избранное` обязан сопоставлять тайтлы между источниками и переключаться без repeated full scans.
- Не возвращать надпись `Загружаем список серий…` и формулировку про список серий `в фоне`.
- Cache должен оставаться осторожным: TTL/лимиты/trim, без бесконечного stale-cache и без лишнего расхода интернета.
