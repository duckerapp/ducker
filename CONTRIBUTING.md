# How to help

**English** · [Русский](docs/CONTRIBUTING.ru.md)

Thank you for looking in. ducker is a [mihomo](https://github.com/MetaCubeX/mihomo) client for
Android, a fork of [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) with
its own interface. The project is run by one person, so small, clear changes work best.

## Report a bug or suggest an idea

- A bug — through [Issues](https://github.com/duckerapp/ducker/issues), using the bug template. The
  log from the app (**Settings → Help and diagnostics → Log → Send**) is almost always needed: without it the cause
  cannot be found.
- An idea — using the feature template. Describe the problem it solves, not only how it should
  look.
- A question — in the [Telegram chat](https://t.me/ducker_community) or by email:
  [duckermihomo@proton.me](mailto:duckermihomo@proton.me).
- **A vulnerability** — not in public Issues, but as described in [SECURITY.md](SECURITY.md).

**Never post** in Issues or pull requests: a subscription link, a config file, keys, node
addresses or a device ID. Subscription URLs, configs and keys may grant access; addresses
and device IDs can identify your service or device. The log from the app
removes this data itself, but look through it before sending: the domains of the sites you opened
stay in it.

## Build from source

You need:

- JDK 21;
- the Android SDK: platform 37, NDK **29.0.14206865** (the version is pinned), CMake from the SDK;
- curl and patch: the build downloads its own Go into `~/.cache/ducker` — the version pinned in
  `core/toolchain/go.sh`, checked against its SHA-256 and patched for Android, the same as in CI and
  in releases (Linux x86-64 or macOS);
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
./gradlew :app:lintMetaRelease
./gradlew :app:assembleMetaRelease -Pabi=arm64-v8a,armeabi-v7a
python3 -m unittest discover -s scripts/tests
# Go race checks and their Android DNS overlay: .github/workflows/build.yaml
```

## Dependencies

Gradle checks every file it downloads against its SHA-256 in `gradle/verification-metadata.xml`
and stops the build on a mismatch or a missing sum. After adding or updating a dependency, a plugin
or AGP, record the new sums and read the diff: the new entries should be what you changed and
nothing else.

```
GRADLE_USER_HOME=$(mktemp -d) ./gradlew --write-verification-metadata sha256 help \
    :app:assembleMetaRelease :app:testMetaDebugUnitTest
```

`help` resolves every configuration; the two tasks add what is downloaded only while a task runs,
such as aapt2 and lint. The empty Gradle home makes it download everything again (about 600 MB):
a cache filled earlier skips some metadata files that a fresh checkout needs. Gradle on Linux
records only the Linux aapt2, so after an AGP update add the macOS one by hand, next to the Linux
entry: its SHA-256 is that of `aapt2-<version>-osx.jar` from `dl.google.com/dl/android/maven2`.

## How the project is organised

Where things live is described in the [code map](docs/ARCHITECTURE.md): processes, modules, the
main call chains and how the core is updated. Write new code comments in English; older ones
are still in Russian.

Marks like `D-45` and `F-49` in comments are entry numbers in the working journal of decisions and
facts. The journal itself is not published: if you need the reason why something is done the way
it is, ask in an issue and you will get a straight answer.

## Pull requests

1. **One task — one pull request.** The smaller the change, the sooner it is reviewed.
2. **Say what and why** — and how you tested it: the phone, the Android version, what exactly you
   pressed. State what was not checked too. Device checks are needed for affected Android/VPN behavior;
   documentation and host-only changes can use appropriate local checks.
3. **Code style** — as in the neighbouring files: Kotlin in the official style (the profile is in
   `.idea/codeStyles`); comments explain "why", not "what".
4. **Interface strings** — only in resources. Maintain English (`values/`) and the ten shipped
   translations: Russian, Simplified Chinese, Persian, Arabic, Spanish, French, Indonesian,
   Brazilian Portuguese, Turkish and Vietnamese. Preserve placeholders and explain any
   translation you could not verify; native-speaker corrections are welcome.
5. **The `core`, `service` and `common` modules** — touch them only when there is no other way,
   and explain why in the description. Changes to the mihomo core go to the
   [duckerapp/mihomo](https://github.com/duckerapp/mihomo) fork, branch `ducker`.
6. **Borrowed someone's code or idea?** Name the source as described in
   [docs/CREDITS.md](docs/CREDITS.md).

## License

The project is distributed under GPL-3.0 (see [LICENSE](LICENSE)). By sending a change you agree
that it will be distributed under GPL-3.0 too.
