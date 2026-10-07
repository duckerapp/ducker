# Как помочь проекту

Спасибо, что заглянули. ducker — клиент [mihomo](https://github.com/MetaCubeX/mihomo) для Android,
форк [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) со своим интерфейсом.
Проект ведёт один человек, поэтому лучше всего работают небольшие понятные изменения.

*English summary is at the end.*

## Сообщить об ошибке или предложить идею

- Ошибка — через [Issues](../../issues), по шаблону «Ошибка». Журнал из приложения
  (**Настройки → Журнал → Отправить**) почти всегда нужен: без него причину не найти.
- Идея — по шаблону «Идея». Опишите, какую задачу она решает, а не только как должна выглядеть.
- Вопрос — в [чат в Telegram](https://t.me/ducker_community).
- **Уязвимость** — не в открытые Issues, а по правилам из [SECURITY.md](SECURITY.md).

**Никогда не присылайте** ни в Issues, ни в запросы на слияние: ссылку подписки, файл конфига,
ключи, адреса узлов, идентификатор устройства. По ним любой получит доступ к вашему VPN.
Журнал из приложения эти данные вырезает сам, но просмотрите его перед отправкой: домены
сайтов, которые вы открывали, в нём остаются.

## Собрать из исходников

Нужны:
- JDK 21;
- Android SDK: platform 37, NDK **29.0.14206865** (версия зафиксирована), CMake из SDK;
- Go 1.26 или новее (CI собирает на Go 1.26 от MetaCubeX с патчами из `.github/patch`);
- git.

```
git clone --recurse-submodules https://github.com/duckerapp/ducker.git
cd ducker
./gradlew :app:assembleMetaRelease
```

Без `--recurse-submodules` не соберётся: ядро и форки `utls` и `sing-mux` подключены подмодулями.
Без своего ключа подписи APK подписывается отладочным ключом. Свой ключ подключается файлом
`signing.properties` в корне — формат описан в `build.gradle.kts`.

Проверки, которые запускает CI:

```
./gradlew :app:testMetaDebugUnitTest
./gradlew :core:externalNativeBuildMetaRelease
```

## Как устроен проект

Где что лежит — в [карте кода](docs/ARCHITECTURE.md): процессы, модули, главные цепочки вызовов и
порядок обновления ядра. Она на английском; комментарии в коде и эта страница — на русском.

Пометки вида `D-45` и `F-49` в комментариях — номера записей рабочего журнала решений и фактов.
Сам журнал не опубликован: если нужна причина, по которой сделано так, а не иначе, спросите в
задаче (issue) — ответим по существу.

## Запрос на слияние

1. **Одна задача — один запрос.** Чем меньше изменение, тем быстрее его посмотрят.
2. **Напишите, что и зачем** — и как проверяли: телефон, версия Android, что именно нажимали.
   Изменение без проверки на устройстве не принимается.
3. **Стиль кода** — как в соседних файлах: Kotlin в официальном стиле (профиль в
   `.idea/codeStyles`), комментарии объясняют «почему», а не «что».
4. **Строки интерфейса** — только в ресурсах, на двух языках: `values/strings.xml` и
   `values-ru/strings.xml`.
5. **Модули `core`, `service`, `common`** трогайте, только если без этого никак, и объясните
   в описании, почему. Правки ядра mihomo идут в форк [duckerapp/mihomo](https://github.com/duckerapp/mihomo),
   ветка `ducker`.
6. **Взяли чужой код или идею** — назовите источник по правилам [docs/CREDITS.md](docs/CREDITS.md).

## Лицензия

Проект распространяется под GPL-3.0 (см. [LICENSE](LICENSE)). Отправляя изменение, вы соглашаетесь,
что оно тоже будет распространяться под GPL-3.0.

---

## English summary

ducker is an Android client for mihomo, forked from Clash Meta for Android with its own Compose UI.

- **Bugs and ideas:** open an Issue using the English templates. Attach the app log
  (Settings → Log → Send).
- **Security issues:** see [SECURITY.md](SECURITY.md); do not file them as public Issues.
- **Never post** subscription links, config files, keys, server addresses or device IDs.
- **Build:** JDK 21, Android SDK platform 37, NDK 29.0.14206865, Go 1.26+, then
  `git clone --recurse-submodules` and `./gradlew :app:assembleMetaRelease`.
- **Code map:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — processes, modules, main call
  chains and how the core is updated.
- **Pull requests:** one change per PR, describe what and why, and how you tested it on a device.
  Code comments, the FAQ and this guide are in Russian; the README, the changelog and the code map
  are in English. Contributions are accepted under GPL-3.0.
- **Questions:** the [Telegram chat](https://t.me/ducker_community).
