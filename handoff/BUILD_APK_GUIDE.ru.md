# YORU Android Java 4.12.3 — сборка Release APK

Текущий продукт: стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin-модуль удалён из активного проекта и не участвует в сборке. Forge в этом релизе не используется. C++/NDK в 4.12.3 не добавлялись.

## Локальная сборка

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Итоговый APK:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

В Arena-песочнице Java/JDK может отсутствовать. Если `java` не найден, используйте GitHub Actions на ветке `arena/01a07147-animmmemmemwm`: workflow собирает только `:app:assembleRelease` и сохраняет `apk-output/YORU-4.12.3-release.apk`.

## Проверенный release

- APK: `apk-output/YORU-4.12.3-release.apk`
- SHA256: `8a1b4461866cd17f24bd303b98c1efd1cda0247f2336feb3bc76cc6640f820d0`
- GitHub Actions run: `34043897672`
- Source commit: `c6a1d26`
- APK commit: `05f7800`

## Важные правила продукта

- Активная база — Java YORU 4.12.3, `versionCode 46`.
- Kotlin не возвращать без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- “Озвучка” — только реальные команды перевода/дубляжа. YORU/Yummy/Kodik/AnixSekai/внутренние маршруты — не озвучки.
- Выбранная озвучка — предпочтение и быстрый первый путь; если голоса нет, YORU обязан скрыто найти доступные озвучки/маршруты.
- Для онгоингов показывать `Вышло X из Y`, статус и индивидуальный `nextEpisodeAt`, когда источник отдаёт точную дату.
- Future-серии не должны выглядеть playable: без обычной обложки, play и download.
- Календарь `Избранное` обязан сопоставлять тайтлы между источниками и переключаться без repeated full scans.
- Не возвращать надпись `Загружаем список серий…` и формулировку про список серий `в фоне`.
