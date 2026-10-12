# Credits and attribution

[Русский](CREDITS.ru.md)

The project builds on other people's open-source work. The rule is simple:
**everything borrowed is credited.** This file is kept up to date and linked from the
repository README.

## Foundation

- **Clash Meta for Android (CMFA)** — MetaCubeX, GPL-3.0.
  Ducker is a CMFA fork. The network layer, VPN service, core wrapper, and core build are their work.
- **mihomo** — MetaCubeX, GPL-3.0. The core.
- **zashboard** — Zephyruso, MIT. The diagnostics dashboard under **Advanced**.

## Borrowed ideas and code

Each record states what was borrowed, its source (repository, branch, commit), and whether
it is an idea or code.

| What | Source | Idea or code |
|-----|----------|--------------|
| Storing provider headers in a file in the profile directory and exposing them as `Profile` fields | Prizrak-Box-android, branch `prizrak`, commit `5237799` (`service/.../ProfileProcessor.kt:1427-1543`, `ProfileManager.kt:268-320`) | idea |
| A single Go export for “links + template → config.yaml” | same source, `core/src/main/golang/native/convert.go:19-88` | idea; our implementation, built on `ConvertsV2Ray` |
| HWID from `ANDROID_ID` without hashing, and the accompanying device headers | same source, `ProfileProcessor.kt:216-234`; FlClashX, commit `dc77f6f`, `lib/utils/device_info_service.dart:112-151` | idea |
| Opening external links on a TV through QR codes instead of a browser | Prizrak-Box-android, `app/.../util/UrlOpening.kt:33-48` | idea |
| Importing a subscription on a TV through a local web page | same source, `app/.../tv/TvImportServer.kt`, `app/src/main/assets/tv_import_web.html` | idea |
| Applying provider settings “only when adding / on every update” | FlClashX, `lib/controller.dart:302-319` | idea; our behavior when the header is absent differs |
| HWID screen in settings | Prizrak-Box-android, HWID dialogs | idea |
| Xray multiplexing (mux.cool) with UDP and XUDP; Xray-compatible REALITY (ML-KEM, ML-DSA-65) | jesus (Jolymmiles), via legiz-ru/Prizrak-Core, branch `v1-19-31`: 13 commits from `2197a3ca` to `9090c2c0`; upstream: MetaCubeX/mihomo#2980 | code, in our `ducker` core branch with the original authors |
| Skipping a broken node in a provider rather than rejecting the entire provider | legiz-ru/Prizrak-Core `e265e713` (ported from Medium1992/mihomo-proxy-ros) | code |
| ML-DSA-65 signatures for REALITY in uTLS | jesus, via legiz-ru/prizrak-utls `80ad7038` | code, in our `duckerapp/utls` fork, branch `ducker`, with the original author |
| The generator of the Binder code between the interface and the service, and its runtime (`kaidl/`, `service/src/main/java/com/github/kr328/kaidl/`) | Kr328/kaidl, branch `main`, commit `7710f43` (version 1.15), MIT — `kaidl/LICENSE` | code; Clash Meta for Android takes it as a binary, here it is source in the tree; what was changed — `kaidl/README.md` |

## Recording borrowed work

1. **Code:** preserve the license header, name the source (repository, branch, commit)
   in the commit message, and add a row to the table above.
2. **Idea:** add a table row; write our own implementation.
3. **Verification is mandatory.** Someone else's solution may be wrong, outdated, or intended
   for a different version. Check against the source and on a device.
