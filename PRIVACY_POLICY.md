# Политика конфиденциальности ducker

*English version is below.*

## Коротко

- ducker не собирает статистику, не показывает рекламу и не отправляет разработчику ни журналы,
  ни отчёты об ошибках. Своего сервера, куда что-то уходило бы, у проекта нет.
- В сеть уходит только то, без чего VPN не работает: запрос подписки провайдеру, ваш трафик
  через его серверы и служебные запросы ядра по настройкам профиля. И ещё одно — проверка, не
  вышла ли новая версия ducker. Подробно — ниже.
- Всё остальное хранится только на телефоне и удаляется вместе с приложением.

## Что уходит в сеть и кому

**1. Провайдеру подписки** — когда вы добавляете или обновляете подписку. Запрос идёт по ссылке,
которую вы дали, и в нём:
- строка `User-Agent` вида `mihomo/<версия ядра> ducker/<версия приложения>`;
- если включён переключатель **Настройки → Идентификатор устройства** (по умолчанию включён):
  идентификатор устройства, слово «Android», версия Android, производитель и модель телефона.
  Идентификатор — это `ANDROID_ID`: Android выдаёт его отдельно каждому приложению на телефоне,
  другие приложения его не видят. Если его нет, ducker один раз создаёт случайный. По
  идентификатору провайдер считает ваши устройства. Какой идентификатор отправляется, видно
  там же, в настройках. Если выключить переключатель, провайдер, который считает устройства,
  может перестать выдавать подписку.

Как и любой сервер, провайдер видит ваш IP-адрес — или адрес своего узла, если VPN включён
и запрос пошёл через него.

Ссылка `ducker://install-config` (также `clash://` и `clashmeta://`) сначала показывает
источник и спрашивает подтверждение. До «Добавить» профиль не скачивается; отмена
не создаёт подписку.

Данные устройства передаются только по HTTPS; по ссылке `http://` уходит один User-Agent. HTTP
оставляет ссылку и профиль доступными сети — перед добавлением или изменением такой ссылки
приложение предупреждает об этом. Куда ведёт ссылка, решает провайдер: списка «разрешённых»
адресов в приложении нет.

Если сервер провайдера переадресует запрос на другой адрес, приложение идёт за переадресацией
(не больше десяти раз), и данные устройства идут следом — пока вся цепочка шла по HTTPS. После
первого адреса `http://` они больше не отправляются, даже если дальше снова HTTPS.

**2. Картинки из подписки.** Иконки групп и логотип провайдера загружаются по адресам, которые
указал сам провайдер.

**3. Ваш трафик.** При включённом VPN трафик приложений идёт через серверы (узлы) провайдера — так
работает любой VPN. Провайдер и его узлы видят то же, что видит любой VPN-сервис; ducker на это
не влияет.

**4. Служебные запросы ядра.** Их задаёт профиль, то есть провайдер, или встроенный шаблон:
- проверка задержки узлов: запрос через проверяемый узел на адрес из профиля, обычно
  `www.gstatic.com/generate_204`; проверка связи в приложении — через узел на
  `cp.cloudflare.com/generate_204`;
- синхронизация времени (NTP), если профиль её включает (встроенный шаблон — `pool.ntp.org`);
- наборы правил и базы адресов, если профиль на них ссылается.

**5. По вашему нажатию.** Ссылки на страницу провайдера, поддержку, оплату и релизы ducker
открываются в браузере только тогда, когда вы сами на них нажали.

**6. На ваш телевизор — только по вашему действию.** Когда вы сканируете в ducker QR-код с экрана
телевизора и подтверждаете отправку, телефон передаёт ссылку подписки на этот телевизор по
домашней сети. Ссылка зашифрована одноразовым ключом из этого QR-кода. Телевизор слушает сеть,
только пока код на экране.

**7. Проверка новой версии ducker.** Примерно дважды в день и при открытии приложение скачивает
файл `update.json` из репозитория проекта на GitHub, а если тот не ответил — с зеркала на GitLab.
О вас и о телефоне в запросе ничего нет: только строка `User-Agent` вида
`ducker/<версия приложения>`. Саму новую версию приложение скачивает с тех же сайтов и только
тогда, когда вы нажали «Обновить». Как и любой сервер, GitHub и GitLab видят ваш IP-адрес — или
адрес узла провайдера, если VPN включён. Перед установкой приложение сверяет контрольную сумму
файла и его подпись: чужой файл оно не поставит.

## Что хранится на телефоне

- подписки: ссылки и скачанные конфиги;
- настройки приложения и ваши правила;
- результаты замеров задержки узлов;
- журнал службы: два файла по 256 КБ, которые перезаписываются по кругу;
- записи падений: два файла до 64 КБ на процесс и локальный журнал ошибок ядра;
- кэш картинок из подписки.

Всё это удаляется вместе с приложением.

**Резервная копия Android.** В облачную копию и при переносе на новый телефон попадают только
настройки. Подписки и конфиги туда не попадают: в них ключи доступа к вашему VPN.

## Журнал

Журнал остаётся на телефоне. Наружу он уходит, только когда вы сами нажмёте **Скопировать**,
**Отправить** или **Сохранить** в разделе **Настройки → Журнал**. Перед этим из него вырезаются адреса узлов, ключи и
идентификатор устройства. Домены сайтов, которые вы открывали, остаются — без них нельзя
разобраться в работе правил. Об этом предупреждает экран, и текст можно просмотреть перед
отправкой. Записи падений Kotlin и хвост ошибок ядра проходят ту же чистку; экран сбоя
показывает и копирует только подготовленный отчёт. Автоматической отправки нет.

## Разрешения и зачем они нужны

- **VPN** — чтобы направлять трафик через туннель.
- **Уведомления** — чтобы показывать, что VPN работает, и присылать напоминание о конце подписки.
- **Камера** — только для сканера QR-кода подписки. Кадры не сохраняются и никуда не уходят.
- **Список приложений** — чтобы вы могли выбрать, какие приложения идут мимо VPN. Список
  остаётся на телефоне.
- **Работа в фоне и запуск после перезагрузки** — чтобы VPN не отключался сам.
- **`INTERACT_ACROSS_USERS`** объявлено в манифесте, но выдать его можно только вручную, через
  отладку по USB (adb). Приложение само его не просит.

## Изменения

Политика меняется вместе с приложением. История изменений — в истории этого файла в репозитории.

## Вопросы

Через [Issues](../../issues) репозитория или письмом на [duckermihomo@proton.me](mailto:duckermihomo@proton.me).
Уязвимости — по правилам [SECURITY.md](SECURITY.md).

---

# ducker privacy policy

**In short:** ducker does not collect analytics, show ads or send logs or crash reports to the
developer. The project has no server of its own.

**What goes to the network and to whom:**
1. **Your subscription provider** — when you add or update a subscription: a `User-Agent`
   (`mihomo/<core version> ducker/<app version>`) and, if **Settings → Device id** is on (default),
   the device id (the per-app `ANDROID_ID` or a random id created once), `Android`, the Android
   version, and the phone's manufacturer and model. The provider also sees your IP address.
   External install-config links require confirmation before any profile download or creation.
   Device data is sent over HTTPS only; an `http://` link gets nothing but the User-Agent. HTTP
   links and profiles remain visible to the network; the app warns about this when adding or
   editing an HTTP link. Where the link leads is the provider's choice: the app has no list of
   "allowed" addresses.
   If the provider's server redirects the request, the app follows (up to ten times) and the
   device data goes along while the whole chain has been HTTPS. After the first `http://`
   address it is no longer sent, even if HTTPS follows.
2. **Images** referenced by the subscription (group icons, provider logo), from addresses set by
   the provider.
3. **Your traffic** goes through the provider's servers while the VPN is on, as with any VPN.
4. **Core service requests** defined by the profile: latency checks through the checked server
   (usually `www.gstatic.com/generate_204`; the in-app link check uses `cp.cloudflare.com`), NTP if
   the profile enables it (the built-in template uses `pool.ntp.org`), and rule sets or address
   databases the profile refers to.
5. **Links you tap** (provider page, support, payment, ducker releases) open in your browser.
6. **Your TV, only when you ask.** When you scan the QR code shown by ducker on a TV and confirm,
   the phone sends the subscription link to that TV over your home network, encrypted with a
   one-time key from that QR code. The TV listens on the network only while the code is shown.
7. **Checking for a new ducker version.** About twice a day and when you open the app, it
   downloads the `update.json` file from the project repository on GitHub or, if that does not
   answer, from the mirror on GitLab. The request says nothing about you or the phone: only a
   `User-Agent` of the form `ducker/<app version>`. The new version itself is downloaded from the
   same sites, and only when you tap **Update**. Like any server, GitHub and GitLab see your IP
   address — or the address of the provider's server while the VPN is on. Before installing, the
   app checks the file's checksum and signature: it will not install a file that is not ours.

**Stored on the device only:** subscriptions and configs, settings and your rules, latency results,
the service log (two rotating 256 KB files), crash records (two files up to 64 KB per process),
the local core error log, and the image cache. Android cloud backup includes
settings only, never subscriptions or configs. Everything is removed with the app.

**Logs** leave the device only when you tap **Copy**, **Send** or **Save** in **Settings → Log**. Server
addresses, keys and the device id are removed first; visited domain names remain. Kotlin crash
records and the tail of core errors use the same scrubber. The crash screen displays and copies
the prepared report; nothing is sent automatically.

**Permissions:** VPN; notifications; camera (QR scanner only, frames are not stored); the app list
(to choose apps that bypass the VPN, kept on the device); background work and start after reboot.
`INTERACT_ACROSS_USERS` is declared but can only be granted manually via adb.

Questions: repository [Issues](../../issues) or email [duckermihomo@proton.me](mailto:duckermihomo@proton.me).
Security reports: [SECURITY.md](SECURITY.md).
