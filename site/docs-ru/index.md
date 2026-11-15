---
title: Главная
template: home.html
hide:
  - navigation
  - toc
---

<section class="dk-hero" markdown>
<p class="dk-word" aria-hidden="true">DUCKER</p>
<div class="dk-slab" markdown>
<svg class="dk-duck" viewBox="27.2 27.4 52.6 42.1" aria-hidden="true" focusable="false"><path fill="currentColor" d="M44.83,28.06 C43.56,28.68 44.79,31.55 46.93,32.90 C47.85,33.50 47.85,33.52 46.64,34.34 C41.29,37.98 38.81,44.40 40.61,50.02 C41.13,51.69 42.57,54.04 44.21,55.91 C44.68,56.44 44.69,56.47 44.51,56.65 C44.33,56.83 44.25,56.82 42.60,56.38 C36.97,54.85 31.09,54.24 29.28,55.00 C28.70,55.24 28.23,55.74 28.02,56.35 C27.73,57.16 27.76,60.53 28.08,62.23 C29.43,69.76 33.84,76.03 39.98,79.17 C41.28,79.85 41.50,79.92 43.37,80.49 C48.58,82.07 55.84,82.38 61.81,81.27 C69.98,79.74 75.15,75.44 76.03,69.46 C76.22,68.15 76.09,65.89 75.77,64.55 C74.92,61.12 73.32,58.60 69.47,54.64 C67.40,52.52 67.38,52.62 69.93,52.59 C71.93,52.56 72.80,52.41 74.34,51.88 C77.41,50.79 79.34,48.51 78.21,47.30 C77.89,46.97 77.63,46.86 76.55,46.68 C74.33,46.33 72.80,45.64 71.35,44.36 L70.45,43.57 L70.45,43.01 C70.45,42.15 70.17,40.17 69.88,39.05 C68.52,33.67 64.50,30.34 58.78,29.84 C58.06,29.77 56.20,29.73 54.63,29.74 C50.95,29.75 49.08,29.47 46.88,28.55 C45.29,27.89 45.22,27.87 44.83,28.06 Z M62.99,41.86 C63.85,42.29 64.19,43.51 63.69,44.38 C63.09,45.44 61.54,45.58 60.75,44.66 C60.11,43.89 60.12,43.01 60.80,42.28 C61.39,41.63 62.22,41.48 62.99,41.86 Z"/></svg>
<div class="dk-hero__inner" markdown>
<div markdown>

# Открытый VPN-клиент для Android и Android TV

На ядре [mihomo](https://github.com/MetaCubeX/mihomo): одна кнопка, честное состояние — и ничего лишнего на экране.
{ .dk-lede }

[Установить](users/install.md){ .dk-btn .dk-btn--solid } [Вопросы и ответы](users/faq.md){ .dk-btn }
{ .dk-actions }

Без рекламы, без аналитики, без отчётов разработчику. На сайте нет скриптов отслеживания; страницы загружаются с GitHub Pages.
{ .dk-quiet }

</div>
<div class="dk-hero__shots" markdown>

![ducker на телевизоре: экран «Прокси»](assets/shots/tv-proxies.png){ .dk-tv }
![ducker на телефоне: главная с кнопкой включения](assets/shots/phone-home.png){ .dk-phone }

</div>
</div>
</div>
</section>

<section class="dk-steps-section dk-on-paper" markdown>
<div class="dk-wrap" markdown>

## Подключиться — три шага

<div class="dk-steps" markdown>
<div markdown>

<span class="dk-step__n" aria-hidden="true">1</span>

### Скачайте APK

Из [выпусков](https://github.com/duckerapp/ducker/releases) на GitHub. `arm64-v8a` подходит почти любому телефону новее 2017 года; [какой файл брать](users/install.md).

</div>
<div markdown>

<span class="dk-step__n" aria-hidden="true">2</span>

### Добавьте подписку

**«Подписки» → «Добавить»**: вставьте ссылку от провайдера, отсканируйте QR-код или выберите файл.

</div>
<div markdown>

<span class="dk-step__n" aria-hidden="true">3</span>

### Нажмите кнопку

Кнопка включения на главной. Android один раз попросит разрешить VPN — и вы подключены.

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

После подъёма туннеля ducker проверяет, отвечает ли выбранный узел на пробный запрос. Если нет — карточка говорит об этом и предлагает самый быстрый узел.

</div>
<div markdown>

### Ошибки словами

Когда VPN остановился или подписка не обновилась, вы видите причину и что делать, а не код ошибки.

</div>
</div>
</div>
<div class="dk-fan" markdown>

![Главная](assets/shots/phone-home.png){ loading=lazy }
![Группы прокси](assets/shots/phone-groups.png){ loading=lazy }
![Подписки](assets/shots/phone-subscriptions.png){ loading=lazy }

</div>
</div>
</section>

<section class="dk-section dk-on-paper" markdown>
<div class="dk-wrap" markdown>

## Всё остальное — когда понадобится


В [вопросах и ответах](users/faq.md) — подписки, шаблоны конфигурации, правила приложений,
раздача по Wi-Fi и обновления. [Все возможности](https://github.com/duckerapp/ducker/blob/main/docs/README.ru.md#возможности)
перечислены в README.

</div>
</section>

<section class="dk-slab dk-tv-slab" markdown>
<div class="dk-split" markdown>
<div markdown>

## На телевизоре — без набора ссылки пультом

Тот же APK работает на Android TV и Google TV. Телевизор показывает QR-код; отсканируйте его в ducker на телефоне, и подписка уйдёт на телевизор по домашней сети, зашифрованная одноразовым ключом, который есть только в этом коде.

[Как поставить ducker на телевизор — по шагам](users/tv.md)

Док слева, светящаяся рамка вокруг выбранного, и каждый экран управляется стрелками пульта.
{ .dk-quiet }

</div>
<div markdown>

![Android TV: подписка добавляется сканированием QR-кода телефоном](assets/shots/tv-add.png){ loading=lazy }

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

![Главная с названием, логотипом и объявлением провайдера](assets/shots/brand-light.png){ .dk-brand .dk-only-light loading=lazy }
![Главная с названием, логотипом и объявлением провайдера](assets/shots/brand-dark.png){ .dk-brand .dk-only-dark loading=lazy }

</div>
</div>
</section>

<section class="dk-on-paper" markdown>
<div class="dk-wrap" markdown>

Аналитика и отчёты разработчику не отправляются.
{ .dk-statement }

<div class="dk-facts" markdown>
<div markdown>

**Что уходит в сеть.** Запрос подписки вашему провайдеру, картинки, на которые она ссылается, проверка новой версии ducker и ваш трафик через серверы провайдера.

</div>
<div markdown>

**API ядра.** Слушает только 127.0.0.1 и закрыт случайным секретом, созданным при установке.

</div>
<div markdown>

**Открытый код.** GPL-3.0. Приложение собирается из репозитория, и сборку может повторить любой.

</div>
</div>

Полный список — в [политике конфиденциальности](users/privacy.md) · [безопасность](help/security.md)
{ .dk-more }

</div>
</section>

<section class="dk-slab dk-end" markdown>
<svg class="dk-duck" viewBox="27.2 27.4 52.6 42.1" aria-hidden="true" focusable="false"><path fill="currentColor" d="M44.83,28.06 C43.56,28.68 44.79,31.55 46.93,32.90 C47.85,33.50 47.85,33.52 46.64,34.34 C41.29,37.98 38.81,44.40 40.61,50.02 C41.13,51.69 42.57,54.04 44.21,55.91 C44.68,56.44 44.69,56.47 44.51,56.65 C44.33,56.83 44.25,56.82 42.60,56.38 C36.97,54.85 31.09,54.24 29.28,55.00 C28.70,55.24 28.23,55.74 28.02,56.35 C27.73,57.16 27.76,60.53 28.08,62.23 C29.43,69.76 33.84,76.03 39.98,79.17 C41.28,79.85 41.50,79.92 43.37,80.49 C48.58,82.07 55.84,82.38 61.81,81.27 C69.98,79.74 75.15,75.44 76.03,69.46 C76.22,68.15 76.09,65.89 75.77,64.55 C74.92,61.12 73.32,58.60 69.47,54.64 C67.40,52.52 67.38,52.62 69.93,52.59 C71.93,52.56 72.80,52.41 74.34,51.88 C77.41,50.79 79.34,48.51 78.21,47.30 C77.89,46.97 77.63,46.86 76.55,46.68 C74.33,46.33 72.80,45.64 71.35,44.36 L70.45,43.57 L70.45,43.01 C70.45,42.15 70.17,40.17 69.88,39.05 C68.52,33.67 64.50,30.34 58.78,29.84 C58.06,29.77 56.20,29.73 54.63,29.74 C50.95,29.75 49.08,29.47 46.88,28.55 C45.29,27.89 45.22,27.87 44.83,28.06 Z M62.99,41.86 C63.85,42.29 64.19,43.51 63.69,44.38 C63.09,45.44 61.54,45.58 60.75,44.66 C60.11,43.89 60.12,43.01 60.80,42.28 C61.39,41.63 62.22,41.48 62.99,41.86 Z"/></svg>
<div class="dk-end__inner" markdown>

## Установить ducker

Android 8 или новее · телефоны и телевизоры · бесплатно
{ .dk-quiet }

[Установить](users/install.md){ .dk-btn .dk-btn--solid } [Канал в Telegram](https://t.me/ducker_mihomo){ .dk-btn } [GitHub](https://github.com/duckerapp/ducker){ .dk-btn }
{ .dk-actions }

</div>
</section>
