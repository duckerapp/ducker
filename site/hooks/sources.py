"""Build site pages from the English repository documents and screenshots.

FAQ, privacy, contributing, security, and credits remain single-source documents.
Rewrite their repository links for the site and remove the language chooser,
editor comments, and the FAQ's manual contents list. Required replacements fail
the build if their source text changes, rather than leaving a broken link.
"""

import re
from pathlib import Path

from mkdocs.structure.files import File

ROOT = Path(__file__).resolve().parents[2]
GH = "https://github.com/duckerapp/ducker"


def _read(rel: str) -> str:
    text = (ROOT / rel).read_text(encoding="utf-8")
    text = re.sub(r"^\*\*English\*\* · [^\n]*\n", "", text, count=1, flags=re.M)
    text = re.sub(r"^\[Русский\]\([^)]*\)\n", "", text, count=1, flags=re.M)
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    return re.sub(r"\n{3,}", "\n\n", text).strip() + "\n"


def _sub(rel: str, text: str, old: str, new: str) -> str:
    if old not in text:
        raise ValueError(f"{rel}: required source text not found: {old!r}")
    return text.replace(old, new)


def _faq() -> str:
    rel = "docs/FAQ.md"
    text = _read(rel)
    text, count = re.subn(r"\n(?:- \[[^\n]*\]\(#[^)]*\)\n)+", "\n", text, count=1)
    if count != 1:
        raise ValueError(f"{rel}: manual contents list not found")
    return _sub(rel, text, "[CONTRIBUTING.md](../CONTRIBUTING.md)",
                "[Contributing](../help/contributing.md)")


def _privacy() -> str:
    rel = "PRIVACY_POLICY.md"
    text = _sub(rel, _read(rel), "# ducker privacy policy", "# Privacy")
    text = _sub(rel, text, "[Issues](../../issues)", f"[Issues]({GH}/issues)")
    text = _sub(rel, text, "[SECURITY.md](SECURITY.md)", "[Security](../help/security.md)")
    return _sub(rel, text, "this file's repository history",
                f"[this file's repository history]({GH}/commits/main/PRIVACY_POLICY.md)")


def _contributing() -> str:
    rel = "CONTRIBUTING.md"
    text = _read(rel)
    text = _sub(rel, text, "[SECURITY.md](SECURITY.md)", "[Security](security.md)")
    text = _sub(rel, text, "[code map](docs/ARCHITECTURE.md)",
                f"[code map]({GH}/blob/main/docs/ARCHITECTURE.md)")
    text = _sub(rel, text, "[docs/CREDITS.md](docs/CREDITS.md)",
                "[Credits](../support/credits.md)")
    return _sub(rel, text, "[LICENSE](LICENSE)", f"[LICENSE]({GH}/blob/main/LICENSE)")


def _security() -> str:
    rel = "SECURITY.md"
    return _sub(rel, _read(rel), "**Security → Report a vulnerability**",
                f"**Security → Report a vulnerability** ([open the form]({GH}/security/advisories/new))")


def _credits() -> str:
    rel = "docs/CREDITS.md"
    return _sub(rel, _read(rel), "This file is kept up to date and linked from the\nrepository README.\n", "")


PAGES = {
    "users/faq.md": ("docs/FAQ.md", _faq),
    "users/privacy.md": ("PRIVACY_POLICY.md", _privacy),
    "help/contributing.md": ("CONTRIBUTING.md", _contributing),
    "help/security.md": ("SECURITY.md", _security),
    "support/credits.md": ("docs/CREDITS.md", _credits),
}

SHOTS = ROOT / "docs/images/en"


def on_files(files, config):
    for uri, (_, build) in PAGES.items():
        files.append(File.generated(config, uri, content=build()))
    shots = sorted(SHOTS.glob("*.png"))
    if not shots:
        raise ValueError(f"{SHOTS}: no screenshots found")
    for shot in shots:
        files.append(File.generated(config, f"assets/shots/{shot.name}", abs_src_path=str(shot)))
    return files


def on_serve(server, config, builder):
    for source, _ in PAGES.values():
        server.watch(str(ROOT / source))
    return server
