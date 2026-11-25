# Security

[Русский](docs/SECURITY.ru.md)

## Reporting a vulnerability

**Do not open a public Issue.** Until the problem is fixed, a public description helps
people who might exploit it.

Report privately through **Security → Report a vulnerability** on the repository page.
The project maintainer receives the private report. If that is not possible, email
[duckermihomo@proton.me](mailto:duckermihomo@proton.me).

Include:
- what is vulnerable and how it affects users;
- reproduction steps, ducker version, phone, and Android version;
- an example or log if available, **without real subscription URLs, keys, or node addresses**.

## Scope

- The app: UI, VPN service, Go core wrapper (`core/src/main/golang`), and build setup.
- ducker's changes to mihomo ([duckerapp/mihomo](https://github.com/duckerapp/mihomo), branch `ducker`)
  and the project's `utls` and `sing-mux` forks.

Report upstream mihomo vulnerabilities that do not come from ducker's changes to the core's
authors at [MetaCubeX/mihomo](https://github.com/MetaCubeX/mihomo). Questions about a particular
provider or its servers belong to that provider. ducker is only a client.

## Supported versions

Fixes are released in the next version. Only the latest release is supported.

## What to expect

This is a one-person project, so response times are not guaranteed, but vulnerability reports
are handled first. When the fix is released, the release notes will describe what was fixed.
The reporter will be credited if they wish.
