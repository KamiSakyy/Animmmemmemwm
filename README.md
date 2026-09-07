# YORU Android

Рабочая версия: **4.14.2** (`versionCode 58`), на основе проверенного `YORU-4.14.0-source-handoff.zip`.

- [Уборка интерфейса в 4.14.2](docs/YORU-4.14.2.ru.md)
- [Предложения для следующего обновления — пока только идеи](docs/YORU-next-update-ideas.ru.md)
- [Ускорение в 4.14.1 и ограничения проверки](docs/YORU-4.14.1.ru.md)
- [Аудит исходной 4.14.0](docs/audit/YORU-4.14.0-AUDIT.ru.md)
- Исходники Android-приложения: [`yoru-android/`](yoru-android/).
- Подписанный APK и подтверждение сборки публикуются CI в [`apk-output/`](apk-output/) после успешных тестов, lint и проверки сертификата.

## Сборка

Требуются JDK 17, Android SDK Platform 36 и Build Tools 36.0.0. GitHub Actions устанавливает их автоматически и работает **только** с веткой `arena/01a07b80-animmmemmemwm`.

```bash
cd yoru-android
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Для release используется существующая подпись приложения. В CI она восстанавливается из уже имеющегося в репозитории legacy ZIP; отдельные signing-файлы игнорируются Git и не публикуются в артефактах. Для дальнейших выпусков рекомендуется перенести подписание в защищённое хранилище, сохранив совместимость обновлений.

```bash
./gradlew :app:testDebugUnitTest :app:assembleRelease :app:lintRelease
```

Рабочая ветка отделена от `main`. SDK, локальные инструменты, Gradle-кэши и промежуточные результаты сборки не добавляются в Git.
