# YORU Android Java 4.12.2 — сборка Release APK

Текущий продукт: стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin-модуль удалён из активного проекта и не участвует в сборке. Forge в этом релизе не используется.

## Локальная сборка

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Итоговый APK:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

В Arena-песочнице Java/JDK может отсутствовать. Если `java` не найден, используйте GitHub Actions на ветке `arena/01a07147-animmmemmemwm`: workflow собирает только `:app:assembleRelease` и сохраняет `apk-output/YORU-4.12.2-release.apk`.

## Проверенный release

- APK: `apk-output/YORU-4.12.2-release.apk`
- SHA256: `06499a0ff23bca093255471aa7392c9fbd7d469e82f1b81d1211762a07321008`
- GitHub Actions run: `34042510777`
- Source commit: `0acf1ae`
- APK commit: `3a46fef`

## Важные правила продукта

- Активная база — Java YORU 4.12.2, `versionCode 45`.
- Kotlin не возвращать без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- “Озвучка” — только реальные команды перевода/дубляжа. YORU/Yummy/Kodik/AnixSekai/внутренние маршруты — не озвучки.
- Выбранная озвучка — предпочтение и быстрый первый путь; если голоса нет, YORU обязан скрыто найти доступные озвучки/маршруты.
- Для онгоингов показывать `Вышло X из Y`, статус и индивидуальный `nextEpisodeAt`, когда источник отдаёт точную дату.
- Future-серии не должны выглядеть playable: без обычной обложки, play и download.
- Календарь `Избранное` обязан сопоставлять тайтлы между источниками, а не фильтровать по одному `source:id`.
- Не возвращать надпись `Загружаем список серий…` и формулировку про список серий `в фоне`.
