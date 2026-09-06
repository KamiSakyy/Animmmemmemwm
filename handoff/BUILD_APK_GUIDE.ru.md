# YORU Android Java 4.12.5 — сборка Release APK

Текущий продукт: стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не участвует в активной сборке. Forge/toolchain в этом релизе не используется. C++/NDK в 4.12.5 не добавлялись.

## Локальная сборка

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Итоговый APK:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

В Arena-песочнице Java/JDK может отсутствовать. Если `java` не найден, используйте GitHub Actions на ветке `arena/01a07147-animmmemmemwm`: workflow собирает только `:app:assembleRelease` и сохраняет `apk-output/YORU-4.12.5-release.apk`.

## Проверенный release 4.12.5

- APK: `apk-output/YORU-4.12.5-release.apk`
- SHA256: `970d31677e4973d952dd26f7e65cf03825dfde7d9d22c7c025ffa89f9a35932b`
- GitHub Actions run: `34047539871`
- Source commit: `422052a`
- APK commit: `92e1e20`
- Версия приложения: `versionName 4.12.5`, `versionCode 48`

## Срочно исправлено в 4.12.5

- Календарь после 4.12.4 больше не держит события в отдельном нижнем viewport с конфликтующим скроллом.
- `CalendarScreen` теперь сам является нормальным вертикальным `ScrollView`; шапка, дни, фильтры и карточки событий прокручиваются вместе.
- События календаря всё ещё отрисовываются через `RecyclerView`, но `nestedScrolling` отключён, чтобы жесты не ломались.
- Список событий не делает принудительный `recycler.scrollToPosition(0)` при каждом обновлении, чтобы интерфейс не “уезжал”.
- Календарь читает SQLite/legacy cache сразу, затем обновляет сеть в discovery executor, не блокируя UI.
- Убран `postDelayed(350)` перед startup-warmup.
- Убраны startup `postDelayed(700/12000/18000)` цепочки; проверки уходят в background executors без блокировки входа.
- `MainActivity.onCreate()` сначала показывает мгновенный YORU shell, а полный render запускает следующим UI-pass, чтобы не было чёрного ожидания.
- `ApiRepository` больше не читает `boot.json/counts.json` в `Application.onCreate()`; загрузка ленивая/фоновая.
- `SecureStore` больше не будит AndroidKeyStore в конструкторе; расшифровка ленивая.
- `Ui.text()` до готовности `SecureStore` не вызывает font/settings и не блокирует первый кадр.
- `TrafficMeter` больше не читает encrypted traffic-store при старте приложения.
- Нажатия стали быстрее: touch-анимации сокращены.

## Важные правила продукта

- Активная база — Java YORU 4.12.5, `versionCode 48`.
- Kotlin не возвращать без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальные команды перевода/дубляжа. YORU/Yummy/Kodik/AnixSekai/внутренние маршруты — не озвучки.
- Выбранная озвучка — предпочтение и быстрый первый путь; если голоса нет, YORU обязан скрыто найти доступные озвучки/маршруты.
- Для онгоингов показывать `Вышло X из Y`, статус и индивидуальный `nextEpisodeAt`, когда источник отдаёт точную дату.
- Future-серии не должны выглядеть playable: без обычной обложки, play и download.
- Календарь `Избранное` обязан сопоставлять тайтлы между источниками и переключаться без repeated full scans.
- Cache должен оставаться осторожным: TTL/лимиты/trim, без бесконечного stale-cache и без лишнего расхода интернета.
