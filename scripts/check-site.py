#!/usr/bin/env python3
"""Check rendered local links, including links emitted by theme overrides."""
from html.parser import HTMLParser
from pathlib import Path
import sys
from urllib.parse import unquote, urlsplit


class Page(HTMLParser):
    def __init__(self, path):
        super().__init__()
        self.ids = set()
        self.links = []
        self.feed(path.read_text())

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get("id"):
            self.ids.add(attrs["id"])
        if tag == "a" and attrs.get("href"):
            self.links.append(attrs["href"])
        if tag in ("img", "script") and attrs.get("src"):
            self.links.append(attrs["src"])


def check(root):
    root = root.resolve()
    pages = {p.resolve(): Page(p) for p in root.rglob("*.html")}
    errors = []
    for path, page in pages.items():
        for link in page.links:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                if url.netloc != "duckerapp.github.io":
                    continue
            local = unquote(url.path)
            if local.startswith("/ducker/"):
                target = root / local.removeprefix("/ducker/")
            elif local.startswith("/"):
                errors.append(f"{path.relative_to(root)}: unexpected root link {link}")
                continue
            else:
                target = path.parent / local if local else path
            if target.is_dir():
                target /= "index.html"
            target = target.resolve()
            if not target.exists():
                errors.append(f"{path.relative_to(root)}: missing target {link}")
            elif url.fragment and target in pages and unquote(url.fragment) not in pages[target].ids:
                errors.append(f"{path.relative_to(root)}: missing fragment {link}")
    if errors:
        raise ValueError("\n".join(errors))
    print(f"Rendered links and images checked: {len(pages)} pages")


if __name__ == "__main__":
    try:
        check(Path(sys.argv[1]))
    except (ValueError, OSError) as error:
        sys.exit(str(error))
