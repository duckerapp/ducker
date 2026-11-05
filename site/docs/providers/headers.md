# ducker-* headers

A provider can customize the app with response headers on subscription requests.
In Remnawave, set them in the subscription response settings (`customResponseHeaders`);
no panel code changes are needed. To return them only to ducker, see
[Subscription for ducker](start.md#a-separate-response-for-ducker-in-remnawave).

| Header | Value | Effect |
|---|---|---|
| `ducker-brand-name` | text | Provider name instead of DUCKER in the Home header, notification shade, and Quick Settings tile. |
| `ducker-brand-logo` | an `https://…` image URL or inline `data:…;base64,…` image: PNG, JPEG, WebP, or SVG | Logo to the left of the name on Home and beside the subscription on the Profiles tab. If it fails to load, the ducker icon is used. |
| `ducker-buy-url` | URL | **Renew** button on the subscription card when fewer than three days remain, it has expired, or less than 10% of traffic remains; also used in the upcoming-expiry notification. Without this header, `profile-web-page-url` is used. |
| `ducker-global` | `off` | Hides the Global/Rules switch on the Proxy tab; the mode remains as specified in the config. Without this header or with another value, the switch is shown. |
| `ducker-local-proxy` | `off` | Starts the tunnel with the config's local ports closed (`mixed-port`, `port`, `socks-port`, `redir-port`, `tproxy-port`) and without `listeners`: other apps cannot find a proxy at `127.0.0.1`. The config file itself is not rewritten. It also keeps "Wi-Fi sharing" closed. A header cannot open a port; without this header or with another value, the config is applied as supplied. |
| `ducker-override-user` | `on` or `off` | Reserved for provider settings supplied through headers. The app currently reads it, but it has no effect. |
| `ducker-mirrors` | up to five hosts or `https://…` links, comma-separated | Backup addresses of the subscription, tried when its own link fails. See [Subscription mirrors](#subscription-mirrors). |

## Subscription mirrors

When the subscription's domain is blocked or its server is down, ducker tries other addresses on
its own, and the user notices nothing.

- `ducker-mirrors: sub2.example.com, sub3.example.org` — a bare host means the same link on another
  domain, with its path and token, so one static value serves every user. A full `https://…` link
  is used as is; in Remnawave, `{{SHORT_UUID}}` fills in the user's part.
- `fallback-url: <link>` — Happ's backup link. ducker reads it too, after `ducker-mirrors`.
- `new-url: <link>` or `new-domain: <domain>` — Happ's permanent move. ducker switches the stored
  link only after the new address has given a subscription that passed the core's check; until
  then it keeps updating from the old link and tries the move again on the next update.

On static hosting, where you cannot set headers, write the same values as lines at the top of the
subscription file, before the links or the config, as Happ reads them:

```text
#fallback-url: https://backup.example.org/sub/abc
#ducker-mirrors: sub2.example.com
vless://…
```

ducker reads only these four names from lines, and only from the `#` lines at the top. In a base64
file the lines are encoded with the rest; in a YAML config they are comments. A header of the
same name wins over its line.

How ducker tries them:

- The address that answered last time goes first, then the subscription's own link, then the
  mirrors in your order. A mirror you remove from the header is not used again.
- An address gets 10 seconds to answer while another is left; the last one gets a full minute.
- A failure is no connection, a TLS error (including a wrong certificate), a status other than
  2xx, or a web page instead of a subscription. The panel's device-limit answer
  (`x-hwid-max-devices-reached`, `x-hwid-not-supported`) stops the search: every address would
  say the same.
- If no address answers at all, ducker tries each of them directly (when the VPN is on) and the
  first one through the subscription's own node (when its config is loaded).
- Mirrors of an `https` link must be `https`; an `http` link may have `http` mirrors. Mirrors
  receive the same device headers as the link. Five at most.
- The list comes with every successful update. A response from the link itself without the
  header removes the mirrors; a mirror's response without it keeps them.

A mirror must open the same link: point its domain at the same subscription page, at a copy of it
on another server, or at the panel itself — wherever this path gives the subscription. If a backup
serves it on another path, give that mirror as a full link.

Clients learn mirrors from a successful update, so add them before a block, on another IP or CDN.
For a user whose first download is already blocked, put them in the
[one-tap link](start.md#one-tap-link) as well.

## General rules

- Header names are case-insensitive.
- Text containing Cyrillic characters or newlines is sent as `base64:<…>` (UTF-8),
  just as Remnawave sends `announce`.
- A single dash, `-`, means “not set”. The panel does not allow an empty header;
  a dash clears your value, for example to keep DUCKER in the Home header.
- These headers change only the appearance and behavior listed above. They cannot change
  security-related values: the core API secret, logs, or the app update address.

## Standard headers

Besides `ducker-*`, the app understands standard subscription headers: `profile-title`,
`profile-update-interval`, `profile-web-page-url`, `support-url`, `announce`,
`subscription-userinfo`, `subscription-refill-date`, `x-hwid-*`, and Happ's `fallback-url`,
`new-url` and `new-domain` (see [Subscription mirrors](#subscription-mirrors)).
See [Subscription for ducker](start.md#response-headers) for their effects;
`x-hwid-*` headers are described under [Devices and limits](devices.md).
