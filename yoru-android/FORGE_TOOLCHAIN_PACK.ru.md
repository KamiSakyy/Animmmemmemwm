# YORU Forge ARM64 Toolchain Pack

Цель: отдельный минимальный pack для оффлайн-сборки APK на Android ARM64 без GitHub, без полного Android SDK внутри Forge APK.

## Целевой размер

- Forge APK: до 1 МБ, фактически десятки килобайт.
- Toolchain pack zip: ориентир 100–150 МБ.
- После распаковки: ориентир 220–420 МБ.
- Свободное место для сборки YORU: лучше 1–2 ГБ.

## Поддержка устройств

Pack 0.3.x рассчитан на ARM64-only:

```text
arm64-v8a
```

Для маленького веса `armeabi-v7a`, `x86`, `x86_64` не добавляются.

## Структура zip

```text
platforms/android-36/android.jar
bin/aapt2-arm64-v8a
dex/ecj.jar
dex/d8.jar
dex/apksig.jar
keystore/debug.jks
deps/*.jar
lib/*
manifest/toolchain.json
licenses/*
```

`lib/*` нужен, если `aapt2-arm64-v8a` собран с внешними Android/Termux-совместимыми библиотеками. Forge задаёт `LD_LIBRARY_PATH` на эту папку.

## Обязательные self-test команды внутри Forge

После импорта Forge запускает:

```text
bin/aapt2-arm64-v8a version
/system/bin/dalvikvm -cp dex/ecj.jar org.eclipse.jdt.internal.compiler.batch.Main -version
/system/bin/dalvikvm -cp dex/d8.jar com.android.tools.r8.D8 --version
/system/bin/dalvikvm -cp dex/apksig.jar com.android.apksigner.ApkSignerTool version
```

Если хотя бы один self-test не прошёл, кнопка сборки не должна запускать проект.

## Pipeline сборки YORU в Forge

1. Копирование проекта из выбранной SAF-папки во временную папку приложения.
2. Подготовка `AndroidManifest.xml`: подстановка `applicationId`, добавление package, если его нет.
3. `aapt2 compile` для ресурсов.
4. `aapt2 link` с `android.jar`, assets, versionCode/versionName, minSdk/targetSdk.
5. Генерация `BuildConfig.java`.
6. Компиляция Java через ECJ.
7. DEX через D8.
8. Добавление всех `classes*.dex` в APK.
9. Подпись APK через apksigner/debug keystore.
10. Сохранение готового APK в выбранное пользователем место.

## Что не входит в маленький Forge APK

- Android SDK целиком;
- Gradle;
- Maven cache;
- JDK/OpenJDK;
- multi-ABI binaries;
- debug APK builder для GitHub.

Это вынесено отдельно, чтобы APK Forge оставался маленьким.
