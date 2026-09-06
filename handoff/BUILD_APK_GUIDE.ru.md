# YORU Android Java 4.12.0 — сборка Release APK

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

В Arena-песочнице Java/JDK может отсутствовать. Если `java` не найден, используйте GitHub Actions на ветке `arena/01a07147-animmmemmemwm`: workflow собирает только `:app:assembleRelease` и сохраняет `apk-output/YORU-4.12.0-release.apk`.

## Проверенный release

- APK: `apk-output/YORU-4.12.0-release.apk`
- SHA256: `2e6f7e4ab6cd91d004dec539bc0f8689990a868dd00d2cff12a83c406ef706b3`
- GitHub Actions run: `34040316252`
- Source commit: `5315bdf`
- APK commit: `f157fa0`

## Важные правила продукта

- Активная база — Java YORU 4.12.0, `versionCode 43`.
- Kotlin не возвращать без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- “Озвучка” — только реальные команды перевода/дубляжа: AniDUB, AniLibria.TV, AniMaunt, AnimeVost, AniStar & DEEP, Beyond:Studio, Dream Cast, AniMedia и похожие реальные лейблы.
- YORU/Yummy/Kodik/AnixSekai/внутренние маршруты — не озвучки и не должны так показываться.
- Выбранная озвучка — предпочтение и быстрый первый путь; если голоса нет, YORU обязан скрыто найти доступные озвучки/маршруты, а не завершаться ошибкой.
- Для онгоингов показывать `Вышло X из Y`, статус и индивидуальный `nextEpisodeAt`, когда источник отдаёт точную дату. Будущие серии не должны выглядеть playable.
- Приоритет скорости: сначала выбранная/последняя реальная озвучка, короткие таймауты, на мобильной сети меньше фоновых проверок, fallback только когда нужен.
