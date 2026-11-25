# Авторство и заимствования

[English](CREDITS.md)

Проект основан на чужой открытой работе. Правило простое: **всё, что взято, названо.**
Файл поддерживается в актуальном состоянии. На него ссылается README репозитория.

## Основа
- **Clash Meta for Android (CMFA)** — MetaCubeX, GPL-3.0.
  ducker начинался как форк CMFA. Основа службы VPN, обёртка ядра и его сборка — их работа.
- **mihomo** — MetaCubeX, GPL-3.0. Ядро.
- **zashboard** — Zephyruso, MIT. Панель диагностики в разделе «Помощь и диагностика»: выпуск
  `v3.29.1` (`dist-no-fonts.zip`, сверяется по SHA-256), скачивается при сборке.
- **uTLS** — The Go Authors и refraction-networking, BSD 3-Clause. Отпечатки TLS для ядра. Форк проекта
  `duckerapp/utls`, ветка `ducker`.
- **sing-mux** — nekohasekai (SagerNet), GPL-3.0 или новее. Мультиплексирование для ядра. Форк проекта
  `duckerapp/sing-mux`.
- **meta-rules-dat** — MetaCubeX. Базы стран и сайтов (`geoip.metadb`, `geosite.dat`) для правил по
  странам и категориям, скачиваются при сборке; сам проект собирает их из Loyalsoldier/v2ray-rules-dat,
  v2fly/domain-list-community и других перечисленных у него источников. Он выкладывает только
  ежедневный `latest`, поэтому описание каждого выпуска называет SHA-256 баз внутри.
- **mihomo-rule-sets** — Davoyan. Наборы правил, которые встроенный шаблон ducker загружает с jsDelivr: российские
  сайты (`category-ru`) и адреса (`ips-for-ru`), дополнительные адреса Telegram.
- **mihomo-rule-sets** — legiz-ru. Наборы правил того же шаблона: торрент-трекеры, клиенты и сайты, голосовые
  серверы Discord, заблокированные в России сайты (`category-ban-ru`) и российские приложения (`ru-app-list`).

## Библиотеки в приложении

Под своими лицензиями, без изменений: AndroidX, Jetpack Compose и библиотеки Kotlin (Google, JetBrains;
Apache 2.0); **Coil** с OkHttp (Apache 2.0) — картинки; **zxing-cpp** (Apache 2.0) — сканер QR;
**QR Code generator** от Project Nayuki (MIT) — QR-коды; **sora-editor** от Rosemoe (LGPL 2.1) —
редактор конфига; **SnakeYAML** (Apache 2.0); libyuv (BSD 3-Clause) внутри CameraX. Их лицензии
лежат в самом приложении, в `app/src/main/assets/licenses/`: `libraries.txt` — для этих,
`core.txt` — для Go и всех Go-модулей ядра (пишет `scripts/core-licenses.py`).

## Шрифт и значки

- **Onest** — The Onest Project Authors (Дмитрий Волошин, Андрей Кудрявцев), SIL Open Font
  License 1.1. Шрифт интерфейса, без изменений: версия 2.001 из Google Fonts. Лицензия и
  уведомление лежат в самом приложении, в `app/src/main/assets/licenses/`. Им же набран сайт —
  урезанным до латиницы и кириллицы, начертания 400–900 (`site/docs/assets/fonts/`, вместе с
  лицензией).
- **Material Symbols** — Google, Apache License 2.0. Все значки приложения: стиль Rounded, вес
  500, контуры без изменений из npm-пакета `@material-symbols/svg-500` 0.47.6, хранятся векторами
  в `ui/components/Symbols.kt`, их пишет `scripts/material-symbols.py`. Лицензия и уведомление —
  в `app/src/main/assets/licenses/`.

## Код

- **AndroidLiquidGlass** — Kyant, Apache License 2.0. Шейдеры стиля «Жидкое стекло» взяты из его
  `backdrop/.../internal/Shaders.kt` (коммит `65ab177`) и изменены: поле расстояний скруглённого
  прямоугольника, преломление у края, семиполосная дисперсия и блик по краю — в
  `ui/components/LiquidGlass.kt`. Сама библиотека не подключена. Уведомление — в
  `app/src/main/assets/licenses/`.

## Заимствованные идеи и код
Формат записи: что взято, откуда (репозиторий, ветка, коммит), идея или код.

| Что | Источник | Идея или код |
|-----|----------|--------------|
| Заголовки провайдера файлом в каталоге профиля; HWID из `ANDROID_ID` без хеширования с сопутствующими заголовками устройства и его экран в настройках | Prizrak-Box-android, ветка `prizrak`, коммит `5237799` (`ProfileProcessor.kt:216-234`, `:1427-1543`, `ProfileManager.kt:268-320`, диалоги HWID); для заголовков устройства также FlClashX, коммит `dc77f6f`, `lib/utils/device_info_service.dart:112-151` | идея |
| Форма решения «ссылки + шаблон → config.yaml» одним экспортом в Go | там же, `core/src/main/golang/native/convert.go:19-88` | идея, код свой, поверх `ConvertsV2Ray` |
| Приём подписки на телевизоре по домашней сети | там же, `app/.../tv/TvImportServer.kt`, `app/src/main/assets/tv_import_web.html` (локальная веб-страница) | идея. По QR-коду телевизора подписку можно отправить из ducker на Android или через браузер на iPhone и других устройствах. В обоих случаях передача зашифрована |
| Режим применения настроек от провайдера «только при добавлении / при каждом обновлении» | FlClashX, `lib/controller.dart:302-319` | идея, для `ducker-override-user`: ducker применяет его при каждом обновлении |
| Мультиплекс Xray (mux.cool) с UDP и XUDP; REALITY, совместимый с Xray (ML-KEM, ML-DSA-65) | jesus (Jolymmiles), через legiz-ru/Prizrak-Core, ветка `v1-19-31`: 13 коммитов от `2197a3ca` до `9090c2c0`; в апстриме — MetaCubeX/mihomo#2980 | код в ветке ядра `ducker` с сохранением авторства |
| Пропуск битого узла в провайдере вместо отказа всего провайдера | legiz-ru/Prizrak-Core `e265e713` (порт из Medium1992/mihomo-proxy-ros) | код |
| Подпись ML-DSA-65 для REALITY в uTLS | jesus, через legiz-ru/prizrak-utls `80ad7038` | код в форке `duckerapp/utls`, ветка `ducker`, с сохранением авторства |
| Настройки, общие для процессов приложения: файл `SharedPreferences` у службы и провайдер содержимого для интерфейса | RikkaApps/RikkaX, `multiprocess` 1.0.0, MIT | идея. В ducker используется собственная реализация того же устройства |
| Запасные адреса и переезд подписки: `fallback-url`, `new-url`, `new-domain` и строки `#имя: значение` в начале файла | соглашения Happ | идея; совместимо, читаются так, как их пишет Happ |
| Генератор кода Binder между интерфейсом и службой и его среда выполнения (`kaidl/`, `service/src/main/java/com/github/kr328/kaidl/`) | Kr328/kaidl, ветка `main`, коммит `7710f43` (версия 1.15), MIT — `kaidl/LICENSE` | код. Clash Meta for Android использует готовую библиотеку. Генератор и среда выполнения ducker переписаны на этой основе и входят в исходники проекта. Различия описаны в `kaidl/README.md` |

## Как оформлять заимствование

1. **При заимствовании кода** сохраните заголовок лицензии, укажите источник
   (репозиторий, ветку, коммит) в сообщении коммита и добавьте строку в таблицу выше.
2. **При заимствовании идеи** добавьте строку в таблицу и напишите свою реализацию.
3. **Проверка обязательна.** Чужое решение может быть неверным, устаревшим или
   рассчитанным на другую версию. Решение проверяется по исходникам и на устройстве.
