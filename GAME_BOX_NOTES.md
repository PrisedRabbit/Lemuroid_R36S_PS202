# Game Box Notes

Дата: 2026-04-24

## Где лежит вытянутый APK

- APK: `/tmp/gamebox_inspect/Emulator.apk`
- Декомпил `jadx`: `/tmp/gamebox_inspect/jadx`
- Runtime-конфиг, который мы вытащили отдельно: `/tmp/gamebox_inspect/runtime/retroarch.cfg`

## Что это за приложение

- Пакет: `com.xugame.gameconsole`
- Это не свой уникальный эмулятор. Это обертка над RetroArch.
- В декомпиле есть явная связка с другим пакетом:
  - `com.xugame.gameconsoleMenu`
  - ссылка: `/tmp/gamebox_inspect/jadx/sources/com/xugame/gameconsole/emulator/RetroArchEmulatorActivity.java`

## Что удалось подтвердить

### Аудио

- В дефолтном `retroarch.cfg` внутри APK стоят неидеальные значения:
  - `audio_out_rate = "48000"`
  - `audio_block_frames = "1024"`
  - файл: `/tmp/gamebox_inspect/jadx/resources/res/raw/retroarch.cfg`
- Но в живом runtime-конфиге уже правильные значения:
  - `audio_out_rate = "44100"`
  - `audio_block_frames = "2048"`
  - `audio_sync = "true"`
  - файл: `/tmp/gamebox_inspect/runtime/retroarch.cfg`
- В коде это не случайность. `Game Box` реально берет значения из `AudioManager` и пишет их в конфиг:
  - файл: `/tmp/gamebox_inspect/jadx/sources/com/xugame/gameconsole/preferences/UserPreferences.java`
  - там есть запись `audio_out_rate`, `audio_block_frames`, `audio_sync = true`

### Поведение на живом девайсе

- Во время игры `Game Box` держал:
  - `44100 -> 44100`
  - `UndFrmCnt = 0`
  - `raw underrun counters: partial=0 empty=0`
- По `logcat` `AudioTrack` у него писал почти ровными блоками `2048`.
- Вывод: их хороший звук идет не от магии, а от простого стабильного режима:
  - `44100`
  - `2048`
  - `audio_sync = true`

### SNES core

- У `Game Box` есть сильные следы `Snes9x 2005`:
  - на девайсе была папка `Snes9x 2005` в данных приложения
  - в APK есть `snes9x2005` info/resources
- Сам бинарь core из APK не вытащился:
  - `assets/emulator_config.zip` содержит `.info`, но не `.so`
- Рабочая гипотеза: `Game Box` для SNES использует `snes9x2005`

## Что сделали в Lemuroid

### Аудио-профиль PS202

- Добавлен device profile `PS202` в `libretrodroid`
- Для `PS202` сделано:
  - legacy OpenSLES path
  - forced hardware sample rate
  - low latency выключен
  - fixed callback cadence `2048`
- Это приблизило поведение к `Game Box`

### Подготовка `snes9x2005` для PS202

- В `lemuroid-cores` скачаны upstream libretro бинарники `snes9x2005` для:
  - `armeabi-v7a`
  - `arm64-v8a`
  - `x86`
  - `x86_64`
- Они лежат тут:
  - `/Users/developer/Developing/Lemuroid-kitkat/lemuroid-cores/lemuroid_core_snes9x/src/main/jniLibs/armeabi-v7a/libsnes9x2005_libretro_android.so`
  - `/Users/developer/Developing/Lemuroid-kitkat/lemuroid-cores/lemuroid_core_snes9x/src/main/jniLibs/arm64-v8a/libsnes9x2005_libretro_android.so`
  - `/Users/developer/Developing/Lemuroid-kitkat/lemuroid-cores/lemuroid_core_snes9x/src/main/jniLibs/x86/libsnes9x2005_libretro_android.so`
  - `/Users/developer/Developing/Lemuroid-kitkat/lemuroid-cores/lemuroid_core_snes9x/src/main/jniLibs/x86_64/libsnes9x2005_libretro_android.so`
- В `GameLoader.kt` добавлен PS202-специфичный fallback:
  - для `Build.MODEL == "PS202"` и SNES сначала ищется `libsnes9x2005_libretro_android.so`
  - потом уже обычный `coreID.libretroFileName`
- Файл:
  - `/Users/developer/Developing/Lemuroid-kitkat/retrograde-app-shared/src/main/java/com/swordfish/lemuroid/lib/game/GameLoader.kt`

## Что не удалось добить

### Root / доступ к `/data/data`

- До `adb root` обычный shell работает
- После `adb root` коробка отвечает `restarting adbd as root`, но потом shell-канал зависает
- В результате:
  - `adb shell id` висит
  - `adb shell ls /data/data/...` висит
- Это повторилось и после ребута
- Вывод: текущий `adbd` после переключения в root уходит в полутруп

### База `gameconsoleMenu`

- Есть правдоподобная информация, что живая БД лежит тут:
  - `/data/data/com.xugame.gameconsoleMenu/databases`
- Но это пока не подтверждено чтением файлов, потому что root-канал так и не стал рабочим

## Что еще откопали

- В APK есть autoconfig:
  - `assets/autoconfig/android/MUCH_iReadyGo_i5.cfg`
- Это подтверждает, что `Game Box` и RetroArch знают про конкретное устройство/геймпад

## Практический вывод

- Самый ценный инсайт не про root и не про overclock
- Самый ценный инсайт такой:
  - `Game Box` звучит хорошо из-за простого стабильного режима `44100 + 2048 + audio_sync=true`
  - и, скорее всего, из-за более легкого SNES core `snes9x2005`
- Если копать дальше, самые полезные направления:
  1. Добить реальный root-доступ и вытащить живые данные из `/data/data/com.xugame.gameconsoleMenu`
  2. Дожать установку сборки Lemuroid, где PS202 грузит `snes9x2005`
  3. Сравнить звук уже после фактической установки этой сборки
