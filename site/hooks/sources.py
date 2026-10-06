"""
Страницы сайта из документов репозитория (D-197).

FAQ, политика приватности, CONTRIBUTING, SECURITY и благодарности живут в корне и в docs/ —
их читают на GitHub и на них ссылается приложение. На сайт они попадают отсюда, при каждой
сборке, а не копией: поправили исходник — сайт обновится сам.

По дороге убирается то, что сайту не нужно: английские части (у сайта будет своя английская
версия, этап 17), HTML-комментарии для тех, кто правит текст, ручное оглавление FAQ.
Относительные ссылки переписываются на страницы сайта или на GitHub.

Каждая замена обязана найти свой текст. Если исходник переписали и замена промахнулась,
сборка падает с именем файла — иначе на сайте молча осталась бы битая ссылка.
"""

import re
from pathlib import Path

from mkdocs.structure.files import File

ROOT = Path(__file__).resolve().parents[2]
GH = "https://github.com/duckerapp/ducker"


def _read(rel: str, stop: str) -> str:
    text = (ROOT / rel).read_text(encoding="utf-8")
    if stop not in text:
        raise ValueError(f"{rel}: не найден раздел «{stop.strip()}», с которого начинается английская часть")
    text = text.split(stop)[0]
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    text = re.sub(r"^\*[^\n]*English[^\n]*\*$", "", text, flags=re.M)
    text = re.sub(r"\n{3,}", "\n\n", text).rstrip()
    # Разделитель «---» перед английской частью остаётся висеть в конце.
    if text.endswith("---"):
        text = text[:-3].rstrip()
    return text + "\n"


def _sub(rel: str, text: str, old: str, new: str) -> str:
    if old not in text:
        raise ValueError(f"{rel}: не найден текст для замены: {old!r}")
    return text.replace(old, new)


def _faq() -> str:
    rel = "docs/FAQ.md"
    t = _read(rel, "\n## In English")
    # Ручное оглавление — у Material своё, справа.
    t, n = re.subn(r"\n(?:- \[[^\n]*\]\(#[^)]*\)\n)+", "\n", t, count=1)
    if n != 1:
        raise ValueError(f"{rel}: не найдено оглавление в начале")
    t = _sub(rel, t, "в [CONTRIBUTING.md](../CONTRIBUTING.md)", "на странице [«Как помочь»](../help/contributing.md)")
    return t


def _privacy() -> str:
    rel = "PRIVACY_POLICY.md"
    t = _read(rel, "\n# ducker privacy policy")
    t = _sub(rel, t, "# Политика конфиденциальности ducker", "# Приватность")
    t = _sub(rel, t, "[Issues](../../issues)", f"[Issues]({GH}/issues)")
    t = _sub(rel, t, "по правилам [SECURITY.md](SECURITY.md)", "как описано на странице [«Уязвимости»](../help/security.md)")
    t = _sub(
        rel, t,
        "История изменений — в истории этого файла в репозитории.",
        f"История изменений — в [истории файла]({GH}/commits/main/PRIVACY_POLICY.md) в репозитории.",
    )
    return t


def _contributing() -> str:
    rel = "CONTRIBUTING.md"
    t = _read(rel, "\n## English summary")
    t = _sub(rel, t, "# Как помочь проекту", "# Как помочь")
    t = _sub(rel, t, "[Issues](../../issues)", f"[Issues]({GH}/issues)")
    t = _sub(rel, t, "по правилам из [SECURITY.md](SECURITY.md)", "как описано на странице [«Уязвимости»](security.md)")
    t = _sub(rel, t, "по правилам [docs/CREDITS.md](docs/CREDITS.md)", "как описано на странице [«Благодарности»](../support/credits.md)")
    t = _sub(rel, t, "[LICENSE](LICENSE)", f"[LICENSE]({GH}/blob/main/LICENSE)")
    # Рабочие документы проекта на сайт не выносим — ведём на GitHub.
    t = re.sub(r"\]\(docs/([A-Z-]+\.md)\)", rf"]({GH}/blob/main/docs/\1)", t)
    return t


def _security() -> str:
    rel = "SECURITY.md"
    t = _read(rel, "\n# Security")
    t = _sub(rel, t, "# Безопасность", "# Уязвимости")
    t = _sub(
        rel, t,
        "**Security → Report a vulnerability**. ",
        f"**Security → Report a vulnerability** ([открыть форму]({GH}/security/advisories/new)). ",
    )
    return t


def _credits() -> str:
    rel = "docs/CREDITS.md"
    t = (ROOT / rel).read_text(encoding="utf-8")
    t = _sub(rel, t, "# Авторство и заимствования", "# Благодарности")
    t = _sub(rel, t, "Файл поддерживается в актуальном состоянии и попадает в README репозитория.\n", "")
    return t


PAGES = {
    "users/faq.md": ("docs/FAQ.md", _faq),
    "users/privacy.md": ("PRIVACY_POLICY.md", _privacy),
    "help/contributing.md": ("CONTRIBUTING.md", _contributing),
    "help/security.md": ("SECURITY.md", _security),
    "support/credits.md": ("docs/CREDITS.md", _credits),
}


def on_files(files, config):
    for uri, (_, build) in PAGES.items():
        files.append(File.generated(config, uri, content=build()))
    return files


def on_serve(server, config, builder):
    # mkdocs serve пересобирает сайт и при правке исходников в корне и в docs/.
    for src, _ in PAGES.values():
        server.watch(str(ROOT / src))
    return server
