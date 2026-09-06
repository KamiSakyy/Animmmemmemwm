# YORU Android Java 4.12.7 — сборка Release APK

Текущий продукт: стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin не участвует в активной сборке. Forge/toolchain в этом релизе не используется. C++/NDK в 4.12.7 не добавлялись.

## Локальная сборка

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Итоговый APK:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

В Arena-песочнице Java/JDK может отсутствовать. Если `java` не найден, используйте GitHub Actions на ветке `arena/01a07147-animmmemmemwm`: workflow собирает только `:app:assembleRelease` и сохраняет `apk-output/YORU-4.12.7-release.apk`.

## Проверенный release 4.12.7

- APK: `apk-output/YORU-4.12.7-release.apk`
- SHA256: `d898d9265998d503f67d4530420db91c4812f1a2a7dc3734f1c04e717e5231ec`
- GitHub Actions run: `34050519015`
- Source commits: `95cc581`, `44004aa`
- APK commit: `27d13d7`
- Версия приложения: `versionName 4.12.7`, `versionCode 50`

## Срочно исправлено в 4.12.7

- Календарь больше не `ScrollView` с вложенным списком: теперь это один полноэкранный `RecyclerView`, поэтому события не обрезаются до 2 карточек.
- По умолчанию календарь открывается в режиме `Все` и показывает весь поток событий за 28 дней через прокрутку; чипы дней фильтруют только после нажатия.
- Расписание расширено по источнику данных: больше страниц ongoing/anons и fallback до 120 тайтлов; тайтлы без точной даты получают аккуратные слоты `Скоро`.
- Видимые системные полосы прокрутки скрыты.
- Главная восстановлена с красивым anime hero/poster и без технического текста про изменения приложения.
- Загрузки показывают preview/poster-карточку для скачанных эпизодов, сохраняя background download service.
- Нижняя навигация остаётся строго `Календарь` без цифр/бейджей.
- Стартовый loading/shell экран не возвращался.

## Сохранено из прошлых hotfix

- `MainActivity.onCreate()` сразу вызывает настоящий `render()`.
- SQLite `YoruCache` остаётся bounded: TTL/лимиты/trim для details/schedule/franchise/offline/progress.
- Screen-cache вкладок, fast offline-index, RecyclerView серий, franchise-cache/filter/order, max hidden routes/threads сохранены.
- Плеер не режет качество/буфер под mobile/data-saver default и заранее pre-check следующей серии.

## Важные правила продукта

- Активная база — Java YORU 4.12.7, `versionCode 50`.
- Kotlin не возвращать без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private tokens/API keys и MP4-конвертацию.
- “Озвучка” — только реальные команды перевода/дубляжа. YORU/Yummy/Kodik/AnixSekai/внутренние маршруты — не озвучки.
- Выбранная озвучка — предпочтение и быстрый первый путь; если голоса нет, YORU обязан скрыто найти доступные озвучки/маршруты.
- Future-серии не должны выглядеть playable: без обычной обложки, play и download.
- Календарь `Избранное` обязан сопоставлять тайтлы между источниками и переключаться без repeated full scans.
- Cache должен оставаться осторожным: TTL/лимиты/trim, без бесконечного stale-cache и без лишнего расхода интернета.
