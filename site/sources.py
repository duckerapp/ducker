"""Write the site pages that come from repository documents, for both languages.

Zensical does not run MkDocs hooks, so this script runs before every build:

    python sources.py && zensical build --strict && zensical build --strict -f mkdocs.ru.yml

FAQ, privacy, contributing, security, and credits remain single-source documents. The script
rewrites their repository links for the site and removes the language chooser, editor comments,
and the FAQ's manual contents list. Every replacement must find its text or the script fails:
a rewritten source needs this file fixed at once, not a broken link found later.
It also copies the screenshots of each language and, for the Russian site, the shared assets.
Everything it writes is excluded from Git (`.gitignore`).
"""

import re
import shutil
from pathlib import Path

SITE = Path(__file__).resolve().parent
ROOT = SITE.parent
GH = "https://github.com/duckerapp/ducker"

PAGES = {
    "en": {
        "users/faq.md": ("docs/FAQ.md", [
            ("[CONTRIBUTING.md](../CONTRIBUTING.md)", "[Contributing](../help/contributing.md)"),
        ]),
        "users/privacy.md": ("PRIVACY_POLICY.md", [
            ("# ducker privacy policy", "# Privacy"),
            ("[Issues](../../issues)", f"[Issues]({GH}/issues)"),
            ("[SECURITY.md](SECURITY.md)", "[Security](../help/security.md)"),
            ("this file's repository history",
             f"[this file's repository history]({GH}/commits/main/PRIVACY_POLICY.md)"),
        ]),
        "help/contributing.md": ("CONTRIBUTING.md", [
            ("[SECURITY.md](SECURITY.md)", "[Security](security.md)"),
            ("[code map](docs/ARCHITECTURE.md)", f"[code map]({GH}/blob/main/docs/ARCHITECTURE.md)"),
            ("[docs/CREDITS.md](docs/CREDITS.md)", "[Credits](../support/credits.md)"),
            ("[LICENSE](LICENSE)", f"[LICENSE]({GH}/blob/main/LICENSE)"),
        ]),
        "help/security.md": ("SECURITY.md", [
            ("**Security → Report a vulnerability**",
             f"**Security → Report a vulnerability** ([open the form]({GH}/security/advisories/new))"),
        ]),
        "support/credits.md": ("docs/CREDITS.md", [
            ("This file is kept up to date and linked from the\nrepository README.\n", ""),
        ]),
    },
    "ru": {
        "users/faq.md": ("docs/FAQ.ru.md", [
            ("(CONTRIBUTING.ru.md)", "(../help/contributing.md)"),
        ]),
        "users/privacy.md": ("docs/PRIVACY_POLICY.ru.md", [
            ("# Политика конфиденциальности ducker", "# Приватность"),
            ("[SECURITY.ru.md](SECURITY.ru.md)", "[«Безопасность»](../help/security.md)"),
            ("в истории этого файла в репозитории",
             f"в [истории этого файла в репозитории]({GH}/commits/main/docs/PRIVACY_POLICY.ru.md)"),
        ]),
        "help/contributing.md": ("docs/CONTRIBUTING.ru.md", [
            ("[SECURITY.ru.md](SECURITY.ru.md)", "[«Безопасность»](security.md)"),
            ("[карте кода](ARCHITECTURE.md)", f"[карте кода]({GH}/blob/main/docs/ARCHITECTURE.md)"),
            ("[CREDITS.ru.md](CREDITS.ru.md)", "[«Благодарности»](../support/credits.md)"),
            ("[LICENSE](../LICENSE)", f"[LICENSE]({GH}/blob/main/LICENSE)"),
        ]),
        "help/security.md": ("docs/SECURITY.ru.md", [
            ("**Security → Report a vulnerability**",
             f"**Security → Report a vulnerability** ([открыть форму]({GH}/security/advisories/new))"),
        ]),
        "support/credits.md": ("docs/CREDITS.ru.md", [
            ("Файл поддерживается в актуальном состоянии. На него ссылается README репозитория.\n", ""),
        ]),
    },
}

DOCS = {"en": SITE / "docs", "ru": SITE / "docs-ru"}
SHOTS = {"en": ROOT / "docs/images/en", "ru": ROOT / "docs/images/ru"}


def page(src: str, subs: list[tuple[str, str]], strip_toc: bool) -> str:
    text = (ROOT / src).read_text(encoding="utf-8")
    # The language chooser under the title: "**English** · [Русский](…)", "[English](…) · **Русский**", "[Русский](…)".
    text, count = re.subn(r"^(?:\*\*English\*\* · |\[English\]\(|\[Русский\]\()[^\n]*\n", "", text,
                          count=1, flags=re.M)
    if count != 1:
        raise ValueError(f"{src}: language chooser not found")
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    if strip_toc:
        text, count = re.subn(r"\n(?:- \[[^\n]*\]\(#[^)]*\)\n)+", "\n", text, count=1)
        if count != 1:
            raise ValueError(f"{src}: manual contents list not found")
    for old, new in subs:
        if old not in text:
            raise ValueError(f"{src}: required source text not found: {old!r}")
        text = text.replace(old, new)
    return re.sub(r"\n{3,}", "\n\n", text).strip() + "\n"


def prepare(lang: str) -> None:
    docs = DOCS[lang]
    for uri, (src, subs) in PAGES[lang].items():
        out = docs / uri
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(page(src, subs, uri == "users/faq.md"), encoding="utf-8")

    shots = sorted(SHOTS[lang].glob("*.png"))
    if not shots:
        raise ValueError(f"{SHOTS[lang]}: no screenshots found")
    (docs / "assets/shots").mkdir(parents=True, exist_ok=True)
    for shot in shots:
        shutil.copy2(shot, docs / "assets/shots" / shot.name)

    # The Russian site shares the stylesheet, script, fonts, and images kept in docs/assets.
    if docs != DOCS["en"]:
        for asset in (DOCS["en"] / "assets").glob("*.*"):
            shutil.copy2(asset, docs / "assets" / asset.name)
        shutil.copytree(DOCS["en"] / "assets/fonts", docs / "assets/fonts", dirs_exist_ok=True)


if __name__ == "__main__":
    for lang in PAGES:
        prepare(lang)
