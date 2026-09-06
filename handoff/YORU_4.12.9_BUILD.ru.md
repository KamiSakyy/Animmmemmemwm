# YORU Android Java 4.12.9 — performance/stability pass

## Итог

- Приложение: **YORU**
- Package/Application ID: `app.yoru.mobile`
- Версия: `4.12.9`
- VersionCode: `52`
- Release APK: `apk-output/YORU-4.12.9-release.apk`
- SHA256 APK: `4f3e805ab38b2cbbbb90d28fbc86e5a8373f5664c2f0e5172d410440742f4875`
- Source handoff: `handoff/YORU-4.12.9-source-handoff.zip`
- SHA256 source ZIP: `b48130e22c22b0a8cf83f54bfdf84c4d3162ebcf6e007ce60baec353fbfed600`
- GitHub Actions run: `34058014093` — success
- Source commit: `7b09674 Optimize YORU performance and stability`
- APK commit из CI: `02beb53 Add built YORU 4.12.9 release APK [skip ci]`

## Что исправлено по лагам

1. **Thread storm / лишняя конкуренция**
   - Большие фиксированные pools в `YoruApp` заменены на bounded `ThreadPoolExecutor` с ограниченными очередями.
   - Потоки теперь имеют core-timeout, поэтому после всплеска работы приложение не держит лишние worker'ы.
   - Временные pools в `ApiRepository` уменьшены для all-catalog/detail/schedule/favorites путей.

2. **Долгие блокирующие сети**
   - Основные `HttpURLConnection` timeout'ы уменьшены с профиля “ждать слишком долго” до более быстрых отказов.
   - All-catalog budgets и ожидание избранного/расписания сокращены, чтобы один плохой route не подвешивал общий результат.
   - Episode API в `YummyParser` получил более короткие request timeout'ы.

3. **Постеры и изображения**
   - `ImageLoader` больше не создаёт неограниченный поток задач: очередь bounded, rejection обрабатывается безопасно.
   - Одинаковые URL дедуплицируются: один in-flight запрос обновляет все ожидающие `ImageView`, а не только первую карточку.
   - Image connect/read timeout'ы снижены, чтобы плохие картинки не держали экран.
   - `clear()` теперь очищает и cache, и список ожидающих image waiters.

4. **Поиск и каталог**
   - Debounce поиска увеличен, чтобы ввод текста не запускал тяжёлый запрос на каждую букву.
   - Reset-load каталога отменяет предыдущий `catalogFuture`.
   - Background catalog task проверяет interrupt до и после API-вызова, чтобы старые ответы не перерисовывали экран.

5. **YoruBrain / рекомендации**
   - Добавлен короткий in-memory cache статистики, чтобы горячие UI-пути не пересчитывали её многократно.
   - Genre matching переведён на нормализованный `HashSet`.
   - Sort-score вычисляется один раз в map и не повторяется внутри comparator.

6. **Lifecycle / стабильность закрытия экранов**
   - `MainActivity.onDestroy()` чистит handler callbacks и отменяет UI futures.
   - `PlayerActivity.onPause()` и `onDestroy()` стали null-safe для player/web.
   - WebView перед destroy удаляется из parent безопасно.

## Что специально не ломалось

- `SecureStore` lock'и не удалялись вслепую: JSON/prefs state correctness важнее опасной оптимизации. Вместо этого снижены горячие повторные вычисления вокруг store.
- Фоновые уведомления 4.12.8 сохранены: JobScheduler + AlarmManager fallback, автономный receiver/job, boot/update/time reschedule, без тяжёлого `checkNow()` на каждый вход.
- Сохранены пользовательские UI-правки 4.12.7/4.12.8: нет startup loading shell, нет бейджей календаря, полный календарь, красивый home hero, скрытые scrollbars, preview загрузок, реальные названия озвучек и скрытые route names.

## Проверка сборки

- CI: GitHub Actions `Build Android APK`, run `34058014093`, статус success.
- APK SHA256 проверен локально: `4f3e805ab38b2cbbbb90d28fbc86e5a8373f5664c2f0e5172d410440742f4875`.
- Source ZIP SHA256: `b48130e22c22b0a8cf83f54bfdf84c4d3162ebcf6e007ce60baec353fbfed600`.
- Локальный Gradle в Arena не запускался, потому что в контейнере нет `java`; release APK проверен через CI.
