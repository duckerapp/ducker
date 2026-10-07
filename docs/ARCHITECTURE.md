# Code map

Where things live, so that you do not have to search the tree. Only the stable parts are listed:
modules, packages and entry points.

Code comments are in Russian. Marks like `D-45` and `F-49` in them are explained in
[CONTRIBUTING.md](../CONTRIBUTING.md).

Path shortcuts used below:

- `A` = `app/src/main/java/com/github/kr328/clash`
- `S` = `service/src/main/java/com/github/kr328/clash/service`
- `K` = `core/src/main/java/com/github/kr328/clash/core`
- `G` = `core/src/main/golang/native`

The Java package is still `com.github.kr328.clash`, as in Clash Meta for Android; the application
ID is `app.ducker.android`.

## Rules that must not break

1. **The profile is applied as is.** The app never rewrites config keys silently.
2. **The core API listens only on `127.0.0.1`** and is protected by a random secret created at
   install time.
3. **Nothing is sent anywhere** except the subscription request, images the subscription refers
   to, and what the user explicitly sends (a log, a subscription to their own TV).
4. **Logs are scrubbed before export:** node addresses, UUIDs, passwords and the API secret are
   removed (`A/log/LogScrubber.kt`).

## Processes

| Process | What runs there |
|---|---|
| `app.ducker.android` | The interface (Compose). The Go core is never started here. |
| `app.ducker.android:background` | The VPN service, the core, the Room database, profile downloads. |
| `app.ducker.android:dashboard` | Only zashboard in a WebView (`DashboardActivity`); exits when the panel is closed. |

The interface talks to the service over Binder: `S/remote/I*.kt` (`IRemoteService`,
`IClashManager`, `IProfileManager`). Settings are shared between the two processes by
`S/PreferenceProvider.kt`.

A call into the core goes like this: Kotlin `K/Clash.kt` → `K/bridge/Bridge.kt`
(`external fun native*`) → `core/src/main/cpp/main.c` (JNI) → Go functions marked `//export` in
`G/*.go`.

Because Android may unload the interface process at any time while the VPN keeps running, screens
receive ready-made state from view models and never call the service from layout code.

## Gradle modules

| Module | What is inside |
|---|---|
| `:app` | The interface, activities, receivers, assets (`assets/ducker/template.yaml`; `assets/zashboard` is downloaded by `app/build.gradle.kts` at build time). |
| `:service` | `VpnService`, the core runtime, Room, profiles, Binder implementations. |
| `:core` | JNI (`core/src/main/cpp`), the Go adapter `cfa` (`G`), the Kotlin facade (`K`), the core build (`core/src/foss/golang`). |
| `:common` | Global context, intent constants, `Store`, utilities, `compat/`. |
| `:baselineprofile` | The baseline profile generator (`StartupProfile.kt`). |

Outside Gradle: `site/` is the documentation site (MkDocs Material, see `site/README.md`),
published by `.github/workflows/pages.yaml`.

## `:app` — `A`

- **Root:** `MainApplication`; `HomeActivity` — the only activity with tabs and overlays, it also
  receives `install-config` links; `ExternalControlActivity` — START/STOP/TOGGLE intents;
  `DashboardActivity`; `QrScanActivity`; `TileService`; `RestartReceiver`; `AppCrashedActivity`;
  `ApkBrokenActivity`.
- **`ui/<screen>/`** — a `XxxScreen.kt` with its `XxxViewModel.kt`:
  - `home` — the power button, the node picker, the background-work and VPN-permission sheets;
  - `proxy` — groups and nodes;
  - `subscriptions` — profile dialogs, sharing as a QR code, reordering, and the phone-to-TV
    transfer (`TvTransfer`: the QR payload, encryption, receiving and sending);
  - `settings`; `config` — the YAML editor; `rules` — My rules; `apps` — per-app access;
    `logs`; `probe` — the node test; `update` — the app update sheet.
- **`ui/components/`** — `DuckerScaffold` (the tab dock), `Tv` (the TV layout, the focus frame and
  D-pad navigation helpers), `DuckerSheet` (bottom sheets), `DuckerIcons`, `BounceOverscroll`.
  `ui/theme/` — `Theme` and `ThemeReveal` (the wave on theme change).
- **`log/`** — `LogScrubber` and the service logcat reader. **`remote/`** — binding to the
  service, `StatusClient`. **`store/`** — interface settings.
- **`util/`** — `Clash.kt` (`startClashService()`), `Disguise.kt` (the calculator icon),
  `NodeFailure.kt` (why a node did not answer, in words).
- **`widget/Widgets.kt`** — home screen widgets.
- **`update/`** — updating the app from inside the app: `Feed` (the `update.json` format and the
  choice of a release for this phone), `Download` (mirrors in order, resuming, SHA-256),
  `Updater` (the state the screens read; check, download, signature check, `PackageInstaller`
  session), `UpdateJob` (the twice-a-day check), `UpdateReceiver`, `UpdateNotice`. The sheet is
  `ui/update/UpdateSheet`. Releases are published with `scripts/publish.sh`.

## `:service` — `S`

- **Services and helpers:** `TunService` (`VpnService`, `establish()`), `RemoteService` (Binder),
  `ProfileWorker` (scheduled updates), `TunGuard`, `FlightRecorder` (the always-on log),
  `ExpiryNotice` (subscription expiry notifications), `VerboseLog` (the one-day limit of the
  detailed log), `StopNotice` (why the VPN stopped when nobody asked it to).
- **Binder implementations:** `ClashManager` (the core) and `ProfileManager` (profiles).
  `ProfileProcessor` downloads, validates and commits a profile.
- **`clash/ClashRuntime.kt`** with `clash/module/*Module.kt`: `Tun`, `Configuration`,
  `NetworkObserve`, `AppListCache`, `StaticNotification`, `DynamicNotification`, `Lockdown`,
  `Suspend`, `TimeZone`, `Close`.
- **`data/`** — Room: `Imported`, `Pending`, `Selection` with DAOs and `migrations/`.
- **`store/`** — `ServiceStore`, `UserRulesStore` (`files/user-rules/<uuid>.json`).
  **`model/`** — `Profile`, `UserRule`, `AccessControlMode`.
- **`util/`** — `SubscriptionHeaders.kt` (the `User-Agent` and device headers),
  `ProviderInfo.kt` (subscription and `ducker-*` headers), `Failure.kt` (a table that turns raw
  core and service errors into an explanation and a suggested action; `:app` uses it too).

## `:core`

- **`K/`** — `Clash.kt` (the facade), `bridge/` (native methods and callbacks), `model/` (JSON
  models).
- **`G/`** (the Go module `cfa`) — exports in `main.go`, `app.go`, `config.go`, `convert.go`,
  `tun.go`, `tunnel.go`, `log.go`, `proxy.go`; the logic is in packages of the same names:
  `config/` (user rules placed before the provider's, fetching, overrides), `convert/` (links to
  a config through the built-in template), `tunnel/`, `tun/`, `app/`.
- **`core/src/foss/golang/`** — the main `go.mod` with `replace` directives and three git
  submodules.

### The core and its forks

The mihomo core is a git submodule, not copied code. Each fork keeps the author's clean branch and
a `ducker` branch, which is the clean branch plus our commits on top.

| Submodule | Fork | Clean branch | Why |
|---|---|---|---|
| `clash/` | [duckerapp/mihomo](https://github.com/duckerapp/mihomo) | `Alpha` | Xray-compatible mux (mux.cool), REALITY in step with Xray, skipping a broken node, naming the DNS server that failed. |
| `utls/` | [duckerapp/utls](https://github.com/duckerapp/utls) | `v1.9.0-mod-meta` | Current TLS fingerprints and ML-DSA-65 for REALITY. |
| `sing-mux/` | [duckerapp/sing-mux](https://github.com/duckerapp/sing-mux) | `main` | Checking that a multiplexed session is alive before reusing it. |

The `replace` directives live in the `foss` module because Go honours them only in the main
module of a build.

To see our commits on top of the core:

```
git -C core/src/foss/golang/clash log --oneline origin/Alpha..HEAD
```

### Updating the core

1. In `core/src/foss/golang/clash`, rebase `ducker` onto the new upstream `Alpha` (keep the old
   state in an `archive/ducker-<date>` branch) and run the Go tests of the patched packages.
2. In `core/src/foss/golang`, run `go mod tidy`.
3. `touch core/src/main/cpp/CMakeLists.txt` — CMake reads the core version from git only when it
   configures, and the app would otherwise keep showing the previous one.
4. If a newer mihomo release is now included in the branch, raise `MIHOMO_RELEASE` in
   `S/util/SubscriptionHeaders.kt`: it is sent in the subscription `User-Agent`.
5. Build only the core as a quick check: `./gradlew :core:externalGolangBuildMetaReleaseArm64V8a`.
6. Run `./scripts/smoke.sh` on a connected phone.

Do not clone the core submodule with `--depth`: CMake asks git which branch contains the pinned
commit and fails on a shallow clone.

## Main flows

- **Turning the VPN on:** `HomeActivity` → `A/util/Clash.kt` `startClashService()` → `TunService` →
  `ClashRuntime` → `TunModule` → `Clash.startTun` → `nativeStartTun` → `G/tun.go`.
- **A subscription:** `SubscriptionsViewModel` → `IProfileManager` → `ProfileManager` →
  `ProfileProcessor` → `Clash.fetchAndValid` → `nativeFetchAndValid`. If the answer is a list of
  links rather than YAML — `convertAndApplyTemplate` with `template.yaml`; the same call takes the
  nodes out of a config the core rejected, when the subscription has no working provider config
  yet.
- **My rules:** `RulesScreen` → `UserRulesStore` → on load `ConfigurationModule` or `ClashManager`
  → `Clash.setUserRules` → `G/config/rules.go`.

## Scripts

- `scripts/smoke.sh` — builds a release, installs it on the connected phone and checks that the
  app starts, the tunnel comes up and traffic goes through the node.
- `scripts/release.sh` — raises the version, builds signed APKs, checks the signature and writes
  checksums.
- `scripts/publish.sh` — uploads a built release to GitHub and the GitLab mirror and records it
  in `update.json`: first for the beta channel, then, with `--stable`, for everyone.
- `scripts/baseline-profile.sh` — rebuilds the baseline profile on an emulator.
