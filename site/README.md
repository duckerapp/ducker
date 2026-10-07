# Сайт документации

Сайт ducker на MkDocs Material. Будет жить на `duckerapp.github.io/ducker` после открытия
репозитория; выкладывает его workflow `.github/workflows/pages.yaml`.

## Собрать и посмотреть

```
cd site
pip install -r requirements.txt
mkdocs build --strict     # проверка: любая битая ссылка — ошибка
mkdocs serve              # http://127.0.0.1:8000, пересобирается при правке
```

Готовый сайт — в `site/build/`, в git не попадает.

## Что откуда

- `docs/` — страницы, которые есть только на сайте: главная, установка, раздел для
  провайдеров, сообщество и связь.
- FAQ, приватность, «Как помочь», «Уязвимости», «Благодарности» здесь **не лежат**: при
  сборке их берёт `hooks/sources.py` из `docs/FAQ.ru.md`, `PRIVACY_POLICY.md`,
  `docs/CONTRIBUTING.ru.md`, `SECURITY.md` и `docs/CREDITS.md`. Править — исходники; сайт подтянет сам.
- Снимки экрана — те же, что в `README`: `hooks/sources.py` берёт их из `docs/images/ru` и
  кладёт в `assets/shots`.
- `docs/assets/ducker.css` — чёрно-белая палитра приложения, марка DUCKER.
- `overrides/.icons/ducker/duck.svg` — утка в шапке.

## Правила

- Никаких шрифтов, скриптов и счётчиков со сторонних сайтов: сайт, как и приложение, ничего
  никуда не шлёт. Поэтому `font: false` и нет `repo_url` (с ним Material ходит в API GitHub).
