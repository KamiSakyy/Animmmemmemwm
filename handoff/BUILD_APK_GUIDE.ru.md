# YORU Android Java 4.12.6 — сборка Release APK

Текущий продукт: стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не участвует в активной сборке. Forge/toolchain в этом релизе не используется. C++/NDK в 4.12.6 не добавлялись.

## Локальная сборка

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Итоговый APK:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

В Arena-песочнице Java/JDK может отсутствовать. Если `java` не найден, используйте GitHub Actions на ветке `arena/01a07147-animmmemmemwm`: workflow собирает только `:app:assembleRelease` и сохраняет `apk-output/YORU-4.12.6-release.apk`.

## Проверенный release 4.12.6

- APK: `apk-output/YORU-4.12.6-release.apk`
- SHA256: `eadeaa777b22cde1507ccc4ed11176ba5c379b8546254bb22e3c8d93a0bc3ae8`
- GitHub Actions run: `34049085497`
- Source commit: `1e958c6`
- APK commit: `964994c`
- Версия приложения: `versionName 4.12.6`, `versionCode 49`

## Срочно исправлено в 4.12.6

- Полностью убран стартовый loading/shell экран `YORU / Открываем без ожидания…`.
- `MainActivity.onCreate()` снова вызывает настоящий `render()` сразу, без промежуточной заставки.
- Главная больше не обязана синхронно читать seed/store до первого содержимого: тяжёлые подборки подставляются фоном.
- В нижней навигации кнопка всегда называется просто `Календарь`, без `· N`, цифр или бейджа.
- Календарь по умолчанию показывает весь список событий за период, а не только выбранный день.
- Добавлен верхний день-фильтр `Все · N`; отдельные дни остались быстрыми фильтрами.
- При переключении фильтров календарь возвращается к `Все`, чтобы пользователь сразу видел все аниме.
- Из календаря убраны лишние loading-фразы; если кэш есть — список появляется сразу, если нет — нет отдельной заставки.

## Сохранено из 4.12.5/4.12.4

- `CalendarScreen` остаётся единым вертикальным `ScrollView`; события внутри `RecyclerView` с `nestedScrolling=false`, чтобы скролл не ломался.
- SQLite `YoruCache` остаётся bounded: TTL/лимиты/trim для details/schedule/franchise/offline/progress.
- Screen-cache вкладок, fast offline-index, RecyclerView серий, franchise-cache/filter/order, max hidden routes/threads сохранены.
- Плеер не режет качество/буфер под mobile/data-saver default и заранее pre-check следующей серии.

## Важные правила продукта

- Активная база — Java YORU 4.12.6, `versionCode 49`.
- Kotlin не возвращать без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальные команды перевода/дубляжа. YORU/Yummy/Kodik/AnixSekai/внутренние маршруты — не озвучки.
- Выбранная озвучка — предпочтение и быстрый первый путь; если голоса нет, YORU обязан скрыто найти доступные озвучки/маршруты.
- Future-серии не должны выглядеть playable: без обычной обложки, play и download.
- Календарь `Избранное` обязан сопоставлять тайтлы между источниками и переключаться без repeated full scans.
- Cache должен оставаться осторожным: TTL/лимиты/trim, без бесконечного stale-cache и без лишнего расхода интернета.
