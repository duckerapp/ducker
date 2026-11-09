---
title: Главная
template: home.html
---

<section class="dk-hero" markdown>
<p class="dk-word" aria-hidden="true">DUCKER</p>
<div class="dk-slab" markdown>
<svg class="dk-duck" viewBox="0 0 24 26" aria-hidden="true" focusable="false"><path fill="currentColor" fill-rule="evenodd" d="M8.70 1.00 C9.90 1.55 11.00 1.75 12.70 1.72 C15.90 1.72 18.20 2.60 18.85 5.40 C18.98 6.00 19.05 6.70 19.10 7.20 C19.12 7.35 19.18 7.45 19.30 7.55 C20.00 8.20 20.70 8.50 21.60 8.65 C22.40 8.80 22.60 9.30 22.10 9.85 C21.30 10.60 20.30 11.00 19.10 11.05 C18.20 11.08 17.90 11.20 18.40 11.75 C20.20 13.45 20.95 14.55 21.25 15.95 C21.40 16.60 21.45 17.40 21.40 18.20 L21.20 26.00 L2.30 26.00 L2.20 18.00 C1.75 15.60 1.60 13.40 1.80 12.60 C1.95 12.05 2.30 11.95 3.00 11.95 C4.60 11.95 6.30 12.20 7.90 12.60 C8.30 12.70 8.55 12.60 8.35 12.35 C7.60 11.50 7.10 10.70 6.90 10.00 C6.20 7.70 7.20 5.10 9.35 3.62 C9.85 3.28 9.85 3.25 9.45 3.00 C8.60 2.45 8.10 1.30 8.70 1.00 Z M16.3 7.27 A0.72 0.72 0 1 0 14.86 7.27 A0.72 0.72 0 1 0 16.3 7.27 Z"/></svg>
<div class="dk-hero__inner" markdown>
<div markdown>

# Открытый VPN-клиент для Android и Android TV

На ядре [mihomo](https://github.com/MetaCubeX/mihomo): одна кнопка, честное состояние — и ничего лишнего на экране.
{ .dk-lede }

[Установить](users/install.md){ .dk-btn .dk-btn--solid } [Вопросы и ответы](users/faq.md){ .dk-btn }
{ .dk-actions }

Без рекламы, без аналитики, без отчётов разработчику. Этот сайт тоже ничего никуда не шлёт.
{ .dk-quiet }

</div>
<div class="dk-hero__shots" markdown>

![ducker на телевизоре: экран «Прокси»](assets/shots/tv-proxies.png){ .dk-tv }
![ducker на телефоне: главная с кнопкой включения](assets/shots/phone-home.png){ .dk-phone }

</div>
</div>
</div>
</section>

<section class="dk-section dk-on-paper" markdown>
<div class="dk-wrap dk-split" markdown>
<div markdown>

## Экран под одну задачу

<div class="dk-claims" markdown>
<div markdown>

### Одна кнопка

На главной — кнопка включения, текущий узел с задержкой, срок подписки и остаток трафика. Всё остальное — на соседних вкладках.

</div>
<div markdown>

### Честное состояние

Сразу после подъёма туннеля ducker проверяет, что трафик действительно идёт через узел. Если нет — карточка говорит об этом и предлагает самый быстрый узел.

</div>
<div markdown>

### Ошибки словами

Когда VPN остановился или подписка не обновилась, вы видите причину и что делать, а не код ошибки.

</div>
</div>
</div>
<div class="dk-fan" markdown>

![Главная](assets/shots/phone-home.png)
![Группы прокси](assets/shots/phone-groups.png)
![Подписки](assets/shots/phone-subscriptions.png)

</div>
</div>
</section>

<section class="dk-on-paper" markdown>
<div class="dk-wrap" markdown>

## Всё остальное — когда понадобится

<div class="dk-index" markdown>
<div markdown>

### Подписка в любом виде

Ссылка, QR-код или файл. Готовый конфиг mihomo применяется как есть, простой список ссылок встаёт во встроенный профиль.

</div>
<div markdown>

### Узлы

Группы со своими значками, проверка задержки, избранное, поиск и замер узла: потери и задержка.

</div>
<div markdown>

### Обновляется сам

Новая версия ставится одним касанием с главной, а можно разрешить ставить её самой, ночью.

</div>
<div markdown>

### Мобильная сеть

Своя подписка и свои серверы на случай, когда телефон ушёл с Wi-Fi.

</div>
<div markdown>

### Подключения

Какие приложения куда подключаются, вживую: приложение, сайт, правило, узел, скорость.

</div>
<div markdown>

### Раздача по Wi-Fi

Интернет через VPN для ноутбука или другого телефона — через прокси в вашей сети.

</div>
<div markdown>

### Приложения и мои правила

Приложение, сайт или целую страну — через VPN, мимо него или заблокировать, не открывая YAML.

</div>
<div markdown>

### Не засыпает

Автозапуск после перезагрузки и пошаговая помощь с телефонами, которые любят выключать VPN.

</div>
<div markdown>

### Приватность

Скрыть ducker из недавних, замаскировать значок, закрыть ссылку и конфиг отпечатком пальца.

</div>
</div>
</div>
</section>

<section class="dk-slab dk-tv-slab" markdown>
<div class="dk-split" markdown>
<div markdown>

## На телевизоре — без набора ссылки пультом

Тот же APK работает на Android TV и Google TV. Телевизор показывает QR-код; отсканируйте его в ducker на телефоне, и подписка уйдёт на телевизор по домашней сети, зашифрованная одноразовым ключом, который есть только в этом коде.

Док слева, светящаяся рамка вокруг выбранного, и каждый экран управляется стрелками пульта.
{ .dk-quiet }

</div>
<div markdown>

![Android TV: подписка добавляется сканированием QR-кода телефоном](assets/shots/tv-add.png)

</div>
</div>
</section>

<section class="dk-section dk-on-paper" markdown>
<div class="dk-wrap dk-split" markdown>
<div markdown>

## Провайдерам

ducker работает с обычной подпиской mihomo. Он запрашивает её с таким User-Agent, поэтому панели, которые отдают клиентам mihomo конфиг, отдают его и ducker:

`User-Agent: mihomo/<core> ducker/<app> (android)`
{ .dk-ua }

Название, логотип, объявление и ссылка на продление попадают в приложение заголовками ответа. Код панели править не нужно.

<div class="dk-links" markdown>

- [Подписка для ducker](providers/start.md)
- [Заголовки ducker-*](providers/headers.md)
- [Устройства и лимит](providers/devices.md)
- [Встроенный шаблон](providers/template.md)

</div>
</div>
<div markdown>

![Главная с названием, логотипом и объявлением провайдера](assets/shots/brand-light.png){ .dk-brand .dk-only-light }
![Главная с названием, логотипом и объявлением провайдера](assets/shots/brand-dark.png){ .dk-brand .dk-only-dark }

</div>
</div>
</section>

<section class="dk-on-paper" markdown>
<div class="dk-wrap" markdown>

Ничего не собирается. Своего сервера у проекта нет.
{ .dk-statement }

<div class="dk-facts" markdown>
<div markdown>

**Что уходит в сеть.** Запрос подписки вашему провайдеру, картинки, на которые она ссылается, проверка новой версии ducker и ваш трафик через серверы провайдера.

</div>
<div markdown>

**API ядра.** Слушает только 127.0.0.1 и закрыт случайным секретом, созданным при установке.

</div>
<div markdown>

**Открытый код.** GPL-3.0. Всё, что есть в APK, собирается из репозитория, и сборку может повторить любой.

</div>
</div>

Полный список — в [политике конфиденциальности](users/privacy.md) · [безопасность](help/security.md)
{ .dk-more }

</div>
</section>

<section class="dk-slab dk-end" markdown>
<svg class="dk-duck" viewBox="0 0 24 26" aria-hidden="true" focusable="false"><path fill="currentColor" fill-rule="evenodd" d="M8.70 1.00 C9.90 1.55 11.00 1.75 12.70 1.72 C15.90 1.72 18.20 2.60 18.85 5.40 C18.98 6.00 19.05 6.70 19.10 7.20 C19.12 7.35 19.18 7.45 19.30 7.55 C20.00 8.20 20.70 8.50 21.60 8.65 C22.40 8.80 22.60 9.30 22.10 9.85 C21.30 10.60 20.30 11.00 19.10 11.05 C18.20 11.08 17.90 11.20 18.40 11.75 C20.20 13.45 20.95 14.55 21.25 15.95 C21.40 16.60 21.45 17.40 21.40 18.20 L21.20 26.00 L2.30 26.00 L2.20 18.00 C1.75 15.60 1.60 13.40 1.80 12.60 C1.95 12.05 2.30 11.95 3.00 11.95 C4.60 11.95 6.30 12.20 7.90 12.60 C8.30 12.70 8.55 12.60 8.35 12.35 C7.60 11.50 7.10 10.70 6.90 10.00 C6.20 7.70 7.20 5.10 9.35 3.62 C9.85 3.28 9.85 3.25 9.45 3.00 C8.60 2.45 8.10 1.30 8.70 1.00 Z M16.3 7.27 A0.72 0.72 0 1 0 14.86 7.27 A0.72 0.72 0 1 0 16.3 7.27 Z"/></svg>
<div class="dk-end__inner" markdown>

## Установить ducker

Android 10 или новее · телефоны и телевизоры · бесплатно
{ .dk-quiet }

[Установить](users/install.md){ .dk-btn .dk-btn--solid } [Канал в Telegram](https://t.me/ducker_mihomo){ .dk-btn } [GitHub](https://github.com/duckerapp/ducker){ .dk-btn }
{ .dk-actions }

</div>
</section>
