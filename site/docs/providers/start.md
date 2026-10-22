# Subscription for ducker

ducker works with a standard mihomo subscription: if your panel already serves configs to
Clash Meta or FlClash, nothing needs changing for ducker. Below is what the app sends,
what it accepts, and how to give it a separate response.

## What comes from the app

Subscription requests carry this header:

```
User-Agent: mihomo/<core-version> ducker/<app-version>
```

It starts with `mihomo/`, so a panel which selects its response format by `User-Agent`
returns a mihomo config. Remnawave uses its normal rule for mihomo clients.
The word `ducker` in the same header distinguishes the app from other clients.

The request also includes device data: id, Android version, and model. These and the
device limit are explained under [Devices and limits](devices.md).

## What the app accepts

| What the URL returns | What ducker does |
|---|---|
| A mihomo config that passes core validation | Applies it as supplied, without rewriting config keys. |
| A list of `vless://`, `vmess://`, `ss://`, `trojan://`, or similar links, as plain text or base64 | Inserts the nodes into the [built-in template](template.md). |
| A mihomo config with an error | For a new subscription, inserts its nodes into the [built-in template](template.md) and shows an error card. An existing subscription keeps its previous config. Details below. |
| Anything else, or a config without nodes | Does not add a new subscription and explains why. An existing subscription keeps its previous config. |

**An update error does not leave the user without a VPN.** The new config is downloaded
and validated by the core before replacing the working config. If validation fails —
because of a config error, a server page instead of a subscription, an empty response,
or a config without any nodes — the old config remains and the VPN continues working.
The Profiles tab shows a “Could not update” card with the reason and core error text
that the user can forward to you.

**A config error in a new subscription.** The user has no working config yet, so the app
does not simply reject it. If the file is readable and has a `proxies` section, ducker
takes its nodes, inserts them into the built-in template, and shows a Home card saying
“Error in the provider config”, with a **Copy error** button so the user can send it to you.
A node that the core cannot construct is skipped. Routing comes from the template, not
your config. Once you fix the config, the next update applies it and removes the card.
Nodes from `proxy-providers` are not extracted this way; if no nodes can be obtained,
the subscription is not added.

The URL may redirect to another address; the app follows up to ten redirects.

While the VPN is enabled, subscription updates follow the profile's rules, usually through
a node. If nodes are unavailable, an update through that route cannot succeed; with the
VPN disabled, the request goes directly.

## Response headers

ducker interprets standard subscription headers as follows:

| Header | Effect |
|---|---|
| `profile-title` | Subscription name. |
| `profile-update-interval` | Subscription update interval, in hours. |
| `subscription-userinfo` | Used traffic, limit, and expiry on Home and the subscription card. |
| `subscription-refill-date` | When the traffic counter resets, as Unix time in seconds. |
| `support-url` | **Support** button. |
| `profile-web-page-url` | Provider page; also the **Renew** button if `ducker-buy-url` is absent. |
| `announce` | Announcement on Home. |

Text containing Cyrillic characters is sent as `base64:<…>` in UTF-8.

The app's name, logo, and behavior are customized through [`ducker-*` headers](headers.md).

## A separate response for ducker in Remnawave

To give `ducker-*` headers and your template only to ducker, create a subscription response
rule matching `ducker` in `User-Agent` and place it above the general rule for mihomo clients.
In a rules export it looks like this:

```json
{
  "name": "Mihomo — ducker (Android)",
  "enabled": true,
  "operator": "AND",
  "conditions": [
    {
      "headerName": "user-agent",
      "operator": "CONTAINS",
      "value": "ducker",
      "caseSensitive": false
    }
  ],
  "responseType": "MIHOMO",
  "responseModifications": {
    "headers": [
      { "key": "ducker-brand-name", "value": "Pond" },
      { "key": "ducker-brand-logo", "value": "https://example.com/logo.png" },
      { "key": "ducker-buy-url", "value": "https://example.com/renew" }
    ],
    "subscriptionTemplate": "Default"
  }
}
```

Field names match Remnawave's October 2026 rules export; if the panel has been updated,
check its documentation. No changes to the panel code are needed.

## One-tap link

```
ducker://install-config?url=<subscription-url>&name=<name>&mirror=<backup>&mirror=<backup>
```

Opens the app's add-subscription window with the URL already filled in. The subscription
is downloaded only after the user taps **Add**. Encode the embedded subscription URL
(`https%3A%2F%2F…`). `clash://install-config` and `clashmeta://install-config` work in the same way.

`mirror` is optional and repeats: each is a backup address written as in
[`ducker-mirrors`](headers.md#subscription-mirrors) — a bare host or a full `https://…` link. They
are tried at this first download when the link is already blocked. After that, the list comes from
the header: a response from the link itself without it removes them. When a user shares the
subscription as a QR code or sends it to a TV, ducker passes its current mirrors along.
