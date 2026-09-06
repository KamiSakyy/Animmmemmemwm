# YORU Android Java 4.12.10

YORU — стабильное Java-приложение `app.yoru.mobile` для поиска, карточек, просмотра, загрузок, коллекции и уведомлений о новых сериях. Текущий активный продукт — Java. Kotlin-модуль удалён из Gradle и из активных исходников ветки.

## Главное в 4.12.10

- Ускорен cold start: `SecureStore` остаётся ленивым, но KeyStore/JSON preload запускается в фоне; `afterFirstFrame` больше не дёргает `store.lastEpisodeCheckAt()` на UI-потоке.
- `warmStartup()` теперь быстро возвращает управление UI: `todayScheduleCount()`, `api.seed()`, календарный refresh и SQLite trim уходят в background executors.
- Починен кэш без удаления кэша: `YoruCache` больше не блокирует все методы одним монитором, SQLite trim выполняется throttled, `todayScheduleCount()` кэшируется в памяти.
- HTTP/stream memory-cache в `ApiRepository` переведён с `synchronizedMap` на bounded `ConcurrentHashMap`, длинные request keys заменены на SHA-256, raw response memory limit снижен до 50KB.
- Android 12+ получает branded launch splash YORU через platform attrs вместо пустого чёрного preview.
- Сохранены улучшения 4.12.9: bounded executors, меньшие network/image timeout'ы, image URL dedupe, отмена устаревших search/catalog задач и оптимизации `YoruBrain`.
- Фоновые уведомления 4.12.8 сохранены: JobScheduler + AlarmManager fallback, восстановление после перезагрузки/обновления и статус последней проверки в профиле.

- Календарь переделан в один полноэкранный `RecyclerView`: шапка, дни, фильтры и события прокручиваются вместе, без вложенного списка, который мог показывать только 2 карточки.
- По умолчанию календарь показывает весь поток событий за 28 дней в режиме `Все`; отдельные дни — только ручные фильтры после нажатия.
- Расписание расширено: больше страниц ongoing/anons, fallback до 120 тайтлов и аккуратные слоты `Скоро` для тайтлов без точной даты.
- Видимые системные полосы прокрутки скрыты в основных экранах и диалогах.
- Главная восстановлена с красивым anime hero/poster и без технического текста про изменения приложения.
- Загрузки показывают крупные preview/poster-карточки для скачанных эпизодов.
- Нижняя навигация сохраняет кнопку строго `Календарь` без цифр/бейджей.
- Стартовый loading/shell экран не используется.
- Java-only сборка сохранена: активный модуль только `:app`, версия `4.12.10` / `53`.

## Сборка

```bash
cd yoru-android
./gradlew --no-daemon :app:assembleRelease
```

Готовый файл локально:

```text
yoru-android/app/build/outputs/apk/release/app-release.apk
```

В Arena-песочнице локального JDK может не быть. Ветка содержит workflow `.github/workflows/build-apk.yml`, который собирает release APK на GitHub Actions.

Проверенный APK: `apk-output/YORU-4.12.10-release.apk`.
SHA256: `64749e41e8e098cb8be64033a9fa32ec7bdf4d8b1ba9b9552b21e2b70a2aa434`.

## Правила продолжения

- Не возвращать Kotlin без прямой команды пользователя.
- Не возвращать Clips, “Ещё”, AniList как пользовательский источник, TSM, AnimeGO, JutSu, SameBand, SovetRomantica, Yummy Legacy.
- Не добавлять VPN/VLESS/Xray/proxy/private token и MP4-конвертацию.
- Не возвращать стартовый loading/shell экран, технические startup-тексты и бейджи в `Календарь`.
- Календарь по умолчанию должен показывать весь список событий, а не один день.
- “Озвучка” — только реальная команда перевода/дубляжа: AniDUB, AniLibria.TV, AniMaunt, AnimeVost, AniStar & DEEP, Beyond:Studio, Dream Cast, AniMedia и т.п.
- Скрытые маршруты можно использовать только под капотом и нельзя показывать их как озвучки.
- Выбранная озвучка — быстрый приоритет, но при её отсутствии YORU обязан скрыто найти рабочую реальную озвучку/маршрут.
- Future-серии не должны выглядеть playable: без обычной обложки, play и download.
- Cache должен оставаться bounded: TTL, лимиты, trim.
