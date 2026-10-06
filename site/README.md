# Сайт документации

Сайт ducker на MkDocs Material (этап 17, D-197). Будет жить на `duckerapp.github.io/ducker`
после открытия репозитория; выкладывает его workflow `site/pages.yaml` — его место `.github/workflows/pages.yaml`,
перенести с основной машины (облачная сессия туда пушить не может).

## Собрать и посмотреть

```
cd site
pip install -r requirements.txt
mkdocs build --strict     # проверка: любая битая ссылка — ошибка
mkdocs serve              # http://127.0.0.1:8000, пересобирается при правке
```

Готовый сайт — в `site/build/`, в git не попадает.

## Что откуда

- `docs/` — страницы, которые есть только на сайте: главная, установка, заголовки для
  провайдеров, донаты, контакт.
- FAQ, приватность, «Как помочь», «Уязвимости», «Благодарности» здесь **не лежат**: при
  сборке их берёт `hooks/sources.py` из `docs/FAQ.md`, `PRIVACY_POLICY.md`, `CONTRIBUTING.md`,
  `SECURITY.md` и `docs/CREDITS.md`. Править — исходники; сайт подтянет сам.
- `docs/assets/ducker.css` — чёрно-белая палитра приложения (D-65), марка DUCKER.
- `overrides/.icons/ducker/duck.svg` — утка в шапке, из `docs/brand/duck-mono.svg`.

## Правила

- Никаких шрифтов, скриптов и счётчиков со сторонних сайтов: сайт, как и приложение, ничего
  никуда не шлёт. Поэтому `font: false` и нет `repo_url` (с ним Material ходит в API GitHub).
- Заглушки, которые ждут владельца, помечены `ЗАГЛУШКА` — `grep -rn ЗАГЛУШКА site/docs`.
