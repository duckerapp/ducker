# Credits and attribution

[Русский](CREDITS.ru.md)

The project builds on other people's open-source work. The rule is simple:
**everything borrowed is credited.** This file is kept up to date and linked from the
repository README.

## Foundation

- **Clash Meta for Android (CMFA)** — MetaCubeX, GPL-3.0.
  Ducker is a CMFA fork. The network layer, VPN service, core wrapper, and core build are their work.
- **mihomo** — MetaCubeX, GPL-3.0. The core.
- **zashboard** — Zephyruso, MIT. The diagnostics dashboard under **Help and diagnostics**: the
  release `v3.29.1` (`dist-no-fonts.zip`, checked by its SHA-256), downloaded during the build.
- **uTLS** — The Go Authors and refraction-networking, BSD 3-Clause. TLS fingerprints for the core;
  our fork `duckerapp/utls`, branch `ducker`.
- **sing-mux** — nekohasekai (SagerNet), GPL-3.0 or later. Multiplexing for the core; our fork
  `duckerapp/sing-mux`.
- **meta-rules-dat** — MetaCubeX. The country and site databases (`geoip.metadb`, `geosite.dat`)
  behind the country and category rules, downloaded during the build; that project builds them from
  Loyalsoldier/v2ray-rules-dat, v2fly/domain-list-community and the other sources it lists.

## Libraries in the app

Under their own licences, unmodified: AndroidX, Jetpack Compose and Kotlin's libraries (Google,
JetBrains; Apache 2.0); **Coil** with OkHttp (Apache 2.0) — images; **zxing-cpp** (Apache 2.0) —
the QR scanner; **QR Code generator** by Project Nayuki (MIT) — QR codes; **sora-editor** by Rosemoe
(LGPL 2.1) — the config editor; **SnakeYAML** (Apache 2.0).

## Font and icons

- **Onest** — The Onest Project Authors (Dmitri Voloshin, Andrey Kudryavtsev), SIL Open Font
  License 1.1. The interface font, unmodified: version 2.001 from Google Fonts. Its license and
  notice ship inside the app, in `app/src/main/assets/licenses/`. The website is set in it too,
  cut down to Latin and Cyrillic, weights 400–900 (`site/docs/assets/fonts/`, with its license).
- **Material Symbols** — Google, Apache License 2.0. Every icon in the app: style Rounded, weight
  500, unmodified outlines from the npm package `@material-symbols/svg-500` 0.47.6, stored as
  vectors in `ui/components/Symbols.kt` by `scripts/material-symbols.py`. License and notice in
  `app/src/main/assets/licenses/`.

## Borrowed ideas and code

Each record states what was borrowed, its source (repository, branch, commit), and whether
it is an idea or code.

| What | Source | Idea or code |
|-----|----------|--------------|
| Storing provider headers in a file in the profile directory and exposing them as `Profile` fields | Prizrak-Box-android, branch `prizrak`, commit `5237799` (`service/.../ProfileProcessor.kt:1427-1543`, `ProfileManager.kt:268-320`) | idea |
| A single Go export for “links + template → config.yaml” | same source, `core/src/main/golang/native/convert.go:19-88` | idea; our implementation, built on `ConvertsV2Ray` |
| HWID from `ANDROID_ID` without hashing, and the accompanying device headers | same source, `ProfileProcessor.kt:216-234`; FlClashX, commit `dc77f6f`, `lib/utils/device_info_service.dart:112-151` | idea |
| Opening external links on a TV through QR codes instead of a browser | Prizrak-Box-android, `app/.../util/UrlOpening.kt:33-48` | idea, not in the app now: links open in the browser |
| Receiving a subscription on a TV over the home network | same source, `app/.../tv/TvImportServer.kt`, `app/src/main/assets/tv_import_web.html` (a local web page) | idea; ducker sends it from ducker on the phone, encrypted, by a QR code |
| Applying provider settings “only when adding / on every update” | FlClashX, `lib/controller.dart:302-319` | idea, for `ducker-override-user`: the app reads the header but does not apply it yet |
| HWID screen in settings | Prizrak-Box-android, HWID dialogs | idea |
| Xray multiplexing (mux.cool) with UDP and XUDP; Xray-compatible REALITY (ML-KEM, ML-DSA-65) | jesus (Jolymmiles), via legiz-ru/Prizrak-Core, branch `v1-19-31`: 13 commits from `2197a3ca` to `9090c2c0`; upstream: MetaCubeX/mihomo#2980 | code, in our `ducker` core branch with the original authors |
| Skipping a broken node in a provider rather than rejecting the entire provider | legiz-ru/Prizrak-Core `e265e713` (ported from Medium1992/mihomo-proxy-ros) | code |
| ML-DSA-65 signatures for REALITY in uTLS | jesus, via legiz-ru/prizrak-utls `80ad7038` | code, in our `duckerapp/utls` fork, branch `ducker`, with the original author |
| Settings shared between the app's processes: a `SharedPreferences` file owned by the service and a content provider for the interface | RikkaApps/RikkaX, `multiprocess` 1.0.0, MIT | idea; the library was replaced with our own code of the same design |
| Backup addresses and moves of a subscription: `fallback-url`, `new-url`, `new-domain`, and `#name: value` lines at the top of the file | Happ's conventions | idea; compatible, read as Happ writes them |
| The generator of the Binder code between the interface and the service, and its runtime (`kaidl/`, `service/src/main/java/com/github/kr328/kaidl/`) | Kr328/kaidl, branch `main`, commit `7710f43` (version 1.15), MIT — `kaidl/LICENSE` | code; Clash Meta for Android takes it as a binary, here the generator and the runtime were rewritten on that basis and live in the tree; what differs — `kaidl/README.md` |

## Recording borrowed work

1. **Code:** preserve the license header, name the source (repository, branch, commit)
   in the commit message, and add a row to the table above.
2. **Idea:** add a table row; write our own implementation.
3. **Verification is mandatory.** Someone else's solution may be wrong, outdated, or intended
   for a different version. Check against the source and on a device.
