# Documentation site

The ducker site uses MkDocs Material. It will be available at `duckerapp.github.io/ducker`
after the repository becomes public; `.github/workflows/pages.yaml` publishes it.

## Build and preview

```
cd site
pip install -r requirements.txt
mkdocs build --strict     # validation: any broken link is an error
mkdocs serve              # http://127.0.0.1:8000; rebuilds when files change
```

The built site is in `site/build/`, which is excluded from Git.

## Content sources

- `docs/`: pages used only on the site — home, installation, provider guides, and community.
- FAQ, privacy, contributing, security, and credits are **not stored as duplicate pages here**.
  `hooks/sources.py` generates them from `docs/FAQ.md`, `PRIVACY_POLICY.md`, `CONTRIBUTING.md`,
  `SECURITY.md`, and `docs/CREDITS.md`. Edit the source; the site picks up the change.
- Screenshots are the same as in the English `README`: `hooks/sources.py` copies them from
  `docs/images/en` into `assets/shots`.
- `docs/assets/ducker.css`: the app's black-and-white palette and DUCKER branding.
- `overrides/.icons/ducker/duck.svg`: the duck wearing a hat.
- Original Russian site sources are preserved in the private archive; public Russian
  document versions remain available in `../docs/`.

## Rules

- No fonts, scripts, or analytics from third-party sites. Like the app, this site sends
  nothing externally. Keep `font: false` and omit `repo_url` (Material otherwise calls GitHub's API).
