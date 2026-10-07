# How to help

**English** · [Русский](docs/CONTRIBUTING.ru.md)

Thank you for looking in. ducker is a [mihomo](https://github.com/MetaCubeX/mihomo) client for
Android, a fork of [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) with
its own interface. The project is run by one person, so small, clear changes work best.

## Report a bug or suggest an idea

- A bug — through [Issues](https://github.com/duckerapp/ducker/issues), using the bug template. The
  log from the app (**Settings → Log → Send**) is almost always needed: without it the cause
  cannot be found.
- An idea — using the feature template. Describe the problem it solves, not only how it should
  look.
- A question — in the [Telegram chat](https://t.me/ducker_community).
- **A vulnerability** — not in public Issues, but as described in [SECURITY.md](SECURITY.md).

**Never post** in Issues or pull requests: a subscription link, a config file, keys, node
addresses or a device ID. Anyone who has them gets access to your VPN. The log from the app
removes this data itself, but look through it before sending: the domains of the sites you opened
stay in it.

## Build from source

You need:

- JDK 21;
- the Android SDK: platform 37, NDK **29.0.14206865** (the version is pinned), CMake from the SDK;
- Go 1.26 or newer (CI builds with Go 1.26 from MetaCubeX with the patches from `.github/patch`);
- git.

```
git clone --recurse-submodules https://github.com/duckerapp/ducker.git
cd ducker
./gradlew :app:assembleMetaRelease
```

It will not build without `--recurse-submodules`: the core and the `utls` and `sing-mux` forks
are submodules. Without your own signing key the APK is signed with the debug key. Your key is
set in `signing.properties` in the repository root; the format is described in
`build.gradle.kts`.

The checks that CI runs:

```
./gradlew :app:testMetaDebugUnitTest
./gradlew :core:externalNativeBuildMetaRelease
```

## How the project is organised

Where things live is described in the [code map](docs/ARCHITECTURE.md): processes, modules, the
main call chains and how the core is updated. Code comments are in Russian.

Marks like `D-45` and `F-49` in comments are entry numbers in the working journal of decisions and
facts. The journal itself is not published: if you need the reason why something is done the way
it is, ask in an issue and you will get a straight answer.

## Pull requests

1. **One task — one pull request.** The smaller the change, the sooner it is reviewed.
2. **Say what and why** — and how you tested it: the phone, the Android version, what exactly you
   pressed. A change that was not checked on a device is not accepted.
3. **Code style** — as in the neighbouring files: Kotlin in the official style (the profile is in
   `.idea/codeStyles`); comments explain "why", not "what".
4. **Interface strings** — only in resources, in two languages: `values/strings.xml` and
   `values-ru/strings.xml`.
5. **The `core`, `service` and `common` modules** — touch them only when there is no other way,
   and explain why in the description. Changes to the mihomo core go to the
   [duckerapp/mihomo](https://github.com/duckerapp/mihomo) fork, branch `ducker`.
6. **Borrowed someone's code or idea?** Name the source as described in
   [docs/CREDITS.md](docs/CREDITS.md).

## License

The project is distributed under GPL-3.0 (see [LICENSE](LICENSE)). By sending a change you agree
that it will be distributed under GPL-3.0 too.
