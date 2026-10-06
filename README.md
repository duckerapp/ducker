# ducker

Клиент [mihomo](https://github.com/MetaCubeX/mihomo) для Android: подключение по подписке,
выбор узла, честные задержки — и ничего лишнего на экране.

Сделан на основе [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid):
ядро и служба оттуда, интерфейс свой.

## Чем отличается от родителя

- **Экран под одну задачу.** Главная: включить, посмотреть узел, срок подписки и остаток трафика.
  Всё остальное — на соседних вкладках, которые листаются пальцем.
- **Подписка, а не конфиг.** Провайдер может прислать не готовый конфиг mihomo,
  а просто список узлов — приложение подставит их во встроенный профиль
  с маршрутизацией под Россию: заблокированное идёт через узел, российское напрямую.
- **Брендинг провайдера.** Название, логотип, объявление, ссылки на поддержку и оплату
  приходят заголовками ответа вместе с подпиской.
- **Идентификатор устройства.** Провайдер, который считает устройства, получает
  устойчивый идентификатор — и человек видит в приложении, какой именно.
- **Чёрно-белый вид** и тёмная тема, которая накатывает волной от кнопки.

## Установка

Скачать apk из [релизов](https://github.com/duckerapp/ducker/releases) и открыть на телефоне.
Android попросит разрешить установку из этого источника — это нормально для приложений
не из Play.

Какой файл брать:

- `arm64-v8a` — любой телефон свежее 2017 года;
- `armeabi-v7a` — старый телефон, если первый не ставится.

Минимальная версия Android — 10.

Дальше: «Подписки» → «+» → вставить ссылку от провайдера. Или открыть ссылку вида
`ducker://install-config?url=…` прямо из письма или чата.

Если что-то не получается — [вопросы и ответы](docs/FAQ.md): как добавить подписку, что делать,
если VPN сам отключается или нет интернета, как прислать журнал.

## Сборка из исходников

Нужны JDK 21, Android SDK (platform 37, NDK 29.0.14206865), Go 1.26 или новее и git.
Подробнее — в [CONTRIBUTING.md](CONTRIBUTING.md).

```
git clone --recurse-submodules https://github.com/duckerapp/ducker.git
cd ducker
./gradlew :app:assembleMetaRelease
```

Без `--recurse-submodules` не соберётся: ядро подключено подмодулем.

Собранное подписывается отладочным ключом. Свой ключ подключается файлом
`signing.properties` в корне: поля `keystore.path`, `keystore.password`, `key.alias`,
`key.password`.

Проверка собранного — `./scripts/smoke.sh`.

## Управление снаружи

Приложение понимает интенты — удобно для Tasker и ярлыков:

Для внешних START/STOP/TOGGLE включите «Настройки → Управление из других приложений».
По умолчанию доступ выключен, в том числе после обновления существующей установки.
Разрешение действует для всех приложений, способных отправить эти команды.
Встроенные виджеты, плитка и ярлыки работают независимо от него; импорт подписки
по ссылке по-прежнему требует подтверждения.

| Действие | Интент |
|---|---|
| включить | `am start -a app.ducker.android.action.START_CLASH` |
| выключить | `am start -a app.ducker.android.action.STOP_CLASH` |
| переключить | `am start -a app.ducker.android.action.TOGGLE_CLASH` |
| добавить подписку | `ducker://install-config?url=<ссылка>` |

## Заголовки для провайдера

Провайдер может настроить вид приложения заголовками ответа на запрос подписки.
В Remnawave они задаются в настройках ответа подписки (`customResponseHeaders`),
править код панели не нужно.

| Заголовок | Значение | Что делает |
|---|---|---|
| `ducker-brand-name` | текст | Название провайдера в шапке главной вместо надписи DUCKER. Оно же — в шторке и на плитке быстрых настроек. |
| `ducker-brand-logo` | ссылка `https://…` на картинку или сама картинка строкой `data:…;base64,…`: PNG, JPEG, WebP или SVG | Логотип слева от названия на главной и у подписки на вкладке «Подписки». Не загрузился — стоит значок ducker. |
| `ducker-buy-url` | ссылка | Кнопка «Продлить» на карточке подписки, когда до конца меньше трёх дней, срок вышел или осталось меньше 10 % трафика; та же ссылка — в уведомлении о скором конце подписки. Без заголовка берётся `profile-web-page-url`. |
| `ducker-global` | `off` | Прячет переключатель Global/Rules на вкладке «Прокси»; режим остаётся тем, что задан в конфиге. Без заголовка или с другим значением переключатель есть. |
| `ducker-local-proxy` | `off` | Туннель поднимается с закрытыми локальными портами конфига (`mixed-port`, `port`, `socks-port`, `redir-port`, `tproxy-port`) и без раздела `listeners`: другие приложения на телефоне не находят прокси на `127.0.0.1`. Сам конфиг не переписывается. Открыть порт заголовком нельзя; без заголовка или с другим значением конфиг применяется как есть. |
| `ducker-override-user` | `on` или `off` | Зарезервирован под настройки, которые провайдер задаёт заголовками. Сейчас приложение его читает, но ни на что он не влияет. |

Общие правила:

- Регистр в имени заголовка не важен.
- Текст с кириллицей или переводом строки передаётся как `base64:<…>` (UTF-8) —
  так же, как Remnawave отдаёт `announce`.
- Значение из одного прочерка `-` значит «не задано». Панель не даёт сохранить пустой
  заголовок, а прочерком можно убрать свой — например, чтобы в шапке осталась надпись DUCKER.
- Заголовки меняют только вид и поведение из списка выше. К безопасности — секрету API
  ядра, журналам, адресу обновлений приложения — у них доступа нет.

Кроме `ducker-*`, приложение понимает общепринятые заголовки подписки: `profile-title`,
`profile-update-interval`, `profile-web-page-url`, `support-url`, `announce`,
`subscription-userinfo`, `subscription-refill-date` и `x-hwid-*`.

## Благодарности

- [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo) — ядро;
- [MetaCubeX/ClashMetaForAndroid](https://github.com/MetaCubeX/ClashMetaForAndroid) —
  служба, сборка и всё, что под интерфейсом;
- [Zephyruso/zashboard](https://github.com/Zephyruso/zashboard) — веб-панель,
  встроенная в приложение.

Подробнее о заимствованиях — [docs/CREDITS.md](docs/CREDITS.md).

## Документы

- [docs/FAQ.md](docs/FAQ.md) — вопросы и ответы для тех, кто пользуется приложением;
- [CONTRIBUTING.md](CONTRIBUTING.md) — как сообщить об ошибке, собрать и прислать изменение;
- [PRIVACY_POLICY.md](PRIVACY_POLICY.md) — что приложение отправляет в сеть и что хранит;
- [SECURITY.md](SECURITY.md) — как сообщить об уязвимости.

## Лицензия

GPL-3.0, как у родительского проекта. Текст — в файле [LICENSE](LICENSE).

---

## In English

ducker is an Android client for [mihomo](https://github.com/MetaCubeX/mihomo), forked from
[Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) with its own Compose UI.
Add a subscription, press one button, pick a server — the rest is on neighbouring tabs.

- Works with mihomo configs and with plain lists of `vless://`, `vmess://` and similar links.
- Provider branding and behaviour via `ducker-*` subscription headers (see the table above).
- No analytics, no ads, no crash reports sent anywhere: [PRIVACY_POLICY.md](PRIVACY_POLICY.md).
- Android 10+. Download APKs from [Releases](https://github.com/duckerapp/ducker/releases):
  `arm64-v8a` for most phones, `armeabi-v7a` for old ones.
- Help for users: [docs/FAQ.md](docs/FAQ.md) — in Russian, with a short English part at the end.
- Build: JDK 21, Android SDK platform 37, NDK 29.0.14206865, Go 1.26+,
  `git clone --recurse-submodules` and `./gradlew :app:assembleMetaRelease`.
- Contributing: [CONTRIBUTING.md](CONTRIBUTING.md). Security: [SECURITY.md](SECURITY.md).
  Project docs are in Russian.

License: GPL-3.0.
