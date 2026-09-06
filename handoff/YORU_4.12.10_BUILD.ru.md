# YORU Android Java 4.12.10 — cold start и cache contention hotfix

## Итог

- Приложение: **YORU**
- Package/Application ID: `app.yoru.mobile`
- Версия: `4.12.10`
- VersionCode: `53`
- Release APK: `apk-output/YORU-4.12.10-release.apk`
- SHA256 APK: `64749e41e8e098cb8be64033a9fa32ec7bdf4d8b1ba9b9552b21e2b70a2aa434`
- Source handoff: `handoff/YORU-4.12.10-source-handoff.zip`
- SHA256 source ZIP: `05ac734bb1f78be59ff976c81ef116928dd0fab59b58c7a4939799bd948a9087`
- GitHub Actions run: `34059097363` — success
- Source commit: `b1ad904 Reduce YORU startup and cache contention`
- APK commit из CI: `c5bbeaa Add built YORU 4.12.9 release APK [skip ci]` — commit message в CI остался старым, но artifact/file внутри правильный: `YORU-4.12.10-release.apk`.

## Что проверено по присланному анализу

1. В актуальной 4.12.9 `SecureStore.ensure()` уже **не был** в конструкторе, но первый `store.lastEpisodeCheckAt()` вызывался из `afterFirstFrame()` на UI-потоке. Это могло дать jank сразу после первого кадра. В 4.12.10 preload KeyStore/JSON запускается в фоне, а проверка last-check тоже перенесена в background executor.
2. `ApiRepository.ensureBoot()` уже **не был** в конструкторе, но `api.seed()` мог запускаться во время startup warmup. В 4.12.10 warmup уходит в background, поэтому UI не ждёт assets JSON.
3. `YoruCache` действительно был слишком широко `synchronized`; это исправлено без удаления кэша.
4. `Collections.synchronizedMap` в HTTP/stream memory-cache действительно оставался; заменён на bounded concurrent maps.
5. Огромные request keys и 450KB raw memory-cache действительно могли давить на CPU/GC; заменено на SHA-256 key и лимит 50KB.
6. Trim SQLite на каждую запись действительно был лишним; теперь throttled примерно раз в 5 минут на таблицу.

## Что изменено

- `SecureStore.preload()` — явный безопасный ленивый прогрев в фоне, без удаления внутренней синхронизации хранилища.
- `YoruApp.afterFirstFrame()` — больше не делает `store.lastEpisodeCheckAt()` на UI; background notification schedule/checkSoon сохранены.
- `YoruApp.warmStartup()` — `todayScheduleCount()`, `api.seed()`, calendar refresh и `cache.trimNow()` выполняются в фоне.
- `MainActivity.onResume()` — update pulse/schedule перенесён с UI в background executor.
- `YoruCache` — снят общий `synchronized` с public cache methods; добавлены `trimMaybe()` и memory-cache результата `todayScheduleCount()`.
- `ApiRepository` — HTTP/stream caches переведены на `ConcurrentHashMap`, добавлена bounded очистка, SHA-256 request key и лимит 50KB.
- `values-v31/styles.xml` — Android 12+ launch splash с YORU icon/dark background через platform attrs.

## Что не урезалось

- Интерфейс и пользовательские функции не ограничивались: главный экран, каталог, поиск, календарь, коллекция, загрузки, плеер и профиль сохранены.
- Кэш не удалён: он оптимизирован, чтобы ускорять, а не блокировать.
- `SecureStore` correctness-синхронизация сохранена для JSON/prefs состояния.
- Фоновые уведомления 4.12.8 сохранены: JobScheduler + AlarmManager fallback, автономный receiver/job, boot/update/time reschedule, без тяжёлого `checkNow()` на каждый вход.
- UI-правки 4.12.7/4.12.8 сохранены: нет startup loading shell, нет бейджей календаря, полный календарь, красивый home hero, скрытые scrollbars, previews загрузок, реальные названия озвучек и скрытые route names.

## Проверка сборки

- CI: GitHub Actions `Build Android APK`, run `34059097363`, статус success.
- APK SHA256 проверен локально: `64749e41e8e098cb8be64033a9fa32ec7bdf4d8b1ba9b9552b21e2b70a2aa434`.
- Source ZIP SHA256: `05ac734bb1f78be59ff976c81ef116928dd0fab59b58c7a4939799bd948a9087`.
- Локальный Gradle в Arena не запускался, потому что в контейнере нет `java`; release APK проверен через CI.
