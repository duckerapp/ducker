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
| `ducker-local-proxy` | `off` | Starts the tunnel with the config's local ports closed (`mixed-port`, `port`, `socks-port`, `redir-port`, `tproxy-port`) and without `listeners`: other apps cannot find a proxy at `127.0.0.1`. The config file itself is not rewritten. A header cannot open a port; without this header or with another value, the config is applied as supplied. |
| `ducker-override-user` | `on` or `off` | Reserved for provider settings supplied through headers. The app currently reads it, but it has no effect. |

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
`subscription-userinfo`, `subscription-refill-date`, and `x-hwid-*`.
See [Subscription for ducker](start.md#response-headers) for their effects;
`x-hwid-*` headers are described under [Devices and limits](devices.md).
