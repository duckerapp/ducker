# Built-in template

If the URL returns a list of node links (`vless://`, `vmess://`, `ss://`, `trojan://`,
and similar), as plain text or base64 rather than a mihomo config, ducker inserts them
into its own template. The template is also used when a new subscription has a config
error: nodes are taken from its `proxies` section
([details](start.md#what-the-app-accepts)).

The template is a fallback. A complete provider config always takes precedence:
the app applies it as supplied.

## What the template contains

- **Routing for Russia.** Blocked destinations go through a node; Russian sites and
  services go directly, and the local network bypasses the VPN.
- **Groups.** “Основной профиль” (Main profile) lists all nodes and the automatic
  “⚡ AUTO” choice; “Без VPN” (Without VPN) is used for direct connections.
- **Rule sets** are downloaded from URLs in the template and update automatically.
- **No local proxy port:** other apps cannot find a proxy at `127.0.0.1` on the phone.
- **Its own DNS** and time synchronization.
- **The subscription address bypasses the tunnel**, so it can be updated even when
  the nodes do not respond.

The file is in the repository:
[`app/src/main/assets/ducker/template.yaml`](https://github.com/duckerapp/ducker/blob/main/app/src/main/assets/ducker/template.yaml).
It contains detailed comments explaining each decision.

## If you need your own routing

Provide a complete mihomo config as described under [Subscription for ducker](start.md).
The app template is not used in that case.
