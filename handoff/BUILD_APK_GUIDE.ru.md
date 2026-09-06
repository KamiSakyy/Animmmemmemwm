# YORU Android Java 4.11.0 — сборка Release APK

Текущий продукт: стабильное Java-приложение `app.yoru.mobile` в `yoru-android/app`. Kotlin-модуль удалён из проекта и не участвует в сборке. Forge в этом релизе не используется.

## Локальная сборка

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Итоговый APK:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

В этой песочнице Java/JDK может отсутствовать. Если `java` не найден, используйте GitHub Actions на ветке `arena/01a07147-animmmemmemwm`: workflow собирает только `:app:assembleRelease` и сохраняет `apk-output/YORU-4.11.0-release.apk`.

## Важные правила продукта

- Активная база — Java YORU 4.10.0, релиз обновлён до 4.11.0.
- Kotlin не возвращать без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- “Озвучка” — только реальные команды перевода/дубляжа: AniDUB, AniLibria.TV, AniMaunt, AnimeVost, AniStar & DEEP, Beyond:Studio, Dream Cast, AniMedia и похожие реальные лейблы.
- YORU/Yummy/Kodik/AnixSekai/внутренние маршруты — не озвучки и не должны так показываться.
- Приоритет скорости: не сканировать лишние голоса, сначала брать выбранную/последнюю реальную озвучку, на мобильной сети снижать качество и фоновые проверки.
