# Documentation site

The ducker site is built with [Zensical](https://zensical.org), the successor of Material for
MkDocs, which reads `mkdocs.yml` as is. It will be available at `duckerapp.github.io/ducker`
(English) and `duckerapp.github.io/ducker/ru/` (Russian) after the repository becomes public;
`.github/workflows/pages.yaml` publishes it.

## Build and preview

```
cd site
pip install -r requirements.txt
python sources.py                          # pages from the repository documents, both languages
zensical build --strict                    # English into build/; clears build/ first
zensical build --strict -f mkdocs.ru.yml   # Russian into build/ru/
zensical serve                             # http://127.0.0.1:8000; rebuilds when files change
```

`--strict` turns a broken link or anchor into an error. The built site is in `site/build/`,
which is excluded from Git. The language switch links to `/ducker/` and `/ducker/ru/`, so it
works on the published site, not under `zensical serve`.

## Content sources

- `docs/` (English) and `docs-ru/` (Russian): pages used only on the site — home, installation,
  provider guides, community, and roadmap. Keep the two in step: the same files, the same facts.
- FAQ, privacy, contributing, security, and credits are **not stored as duplicate pages here**.
  `sources.py` writes them before each build from `docs/FAQ.md`, `PRIVACY_POLICY.md`,
  `CONTRIBUTING.md`, `SECURITY.md`, `docs/CREDITS.md` and their Russian versions in `../docs/`.
  Edit the source; the site picks up the change. The written files are excluded from Git.
- Screenshots are the same as in the `README`: `sources.py` copies `docs/images/en` and
  `docs/images/ru` into each language's `assets/shots`.
- `docs/assets/`: the look (`ducker.css` — the app's black-and-white palette at poster scale: the
  DUCKER wordmark, ink slabs that invert in the dark theme, the duck peeking over a slab's edge),
  the base64 converter on the headers page (`base64.js`), the duck (`ducker-duck.svg`, the icon's
  silhouette from `duck-mono` cut where the icon's tile ends), and the self-hosted font in
  `fonts/` (Onest, the app's own font, SIL OFL, cut down to Latin and Cyrillic). The Russian site
  gets a copy from `sources.py`.
- `overrides/home.html`: the home page's template — full width, no sidebars. Both `index.md` files
  share one markup; only the texts differ.
- `docs/assets/ducker-logo.svg`: the header logo, the app icon's tile with the duck cut by its edge.

## Rules

- No fonts, scripts, or analytics from third-party sites. Like the app, this site sends
  nothing externally. Keep `font: false` and omit `repo_url` (the theme otherwise calls GitHub's API).
