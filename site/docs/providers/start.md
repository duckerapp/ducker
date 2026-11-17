# Subscription for ducker

ducker works with a standard mihomo subscription: if your panel already serves configs to
Clash Meta or FlClash, nothing needs changing for ducker. Below is what the app sends,
what it accepts, and how to give it a separate response.

## Which panels work

| Panel | ducker gets the mihomo config | Name, traffic, term | Announcement | Device limit | ducker's own headers |
|---|---|---|---|---|---|
| Remnawave | by default | yes | yes | yes | custom response headers |
| PasarGuard | by default | yes | yes | yes | custom response headers |
| Marzban | by default | yes | — | — | — |
| Marzneshin | add a rule `^mihomo` → `clash-meta` above the last one, or give users the `…/clash-meta` link | yes | — | — | — |
| 3x-ui | turn on the Clash subscription and its auto-detect, or give users the `/mihomo/<subid>` link | yes | yes | yes | — |
| Hiddify | give users the `…/clashmeta/` link | yes | — | — | — |

Checked against the panels' code in November 2026: Remnawave 3.4.8, PasarGuard 5.6.0,
Marzban 0.8.4, Hiddify 14.2.0. Hiddify shows an unlimited plan as a date far ahead.
Where a panel gives a plain list of links instead, ducker still connects: it places the nodes
into its [built-in template](template.md).

## What comes from the app

Subscription requests carry this header:

```
User-Agent: mihomo/<core-version> ducker/<app-version> (android)
```

It starts with `mihomo/`, so a panel which selects its response format by `User-Agent`
returns a mihomo config. Remnawave uses its normal rule for mihomo clients.
The word `ducker` in the same header distinguishes the app from other clients, and the last word names
the platform. Rule sets and proxy providers the config downloads are fetched as mihomo fetches them:
with the provider's own `header:`, else the config's `global-ua`, and without the device data.

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
takes its nodes, inserts them into the built-in template, and shows a Home card saying that
the provider's config has an error, with a **Copy the error** button so the user can send it to you.
A node that the core cannot construct is skipped. Routing comes from the template, not
your config. Once you fix the config, the next update applies it and removes the card.
Nodes from `proxy-providers` are not extracted this way; if no nodes can be obtained,
the subscription is not added.

The URL may redirect to another address; the app follows up to ten redirects.

**Encrypted subscriptions.** Remnawave can encrypt a subscription with age in its response
rules. When ducker downloads one, the add window explains it and shows a **Decryption key**
field for the key that starts with `AGE-SECRET-KEY-`. Give users the key separately from the link.

While the VPN is enabled, subscription updates follow the profile's rules, usually through
a node; with the VPN disabled, the request goes directly. If no address of the subscription
answers, ducker tries once more directly and through the subscription's own node
(see [Subscription mirrors](headers.md#subscription-mirrors)).

## A list for the mobile network

On **Features → Mobile network** ducker switches to a chosen subscription and node list when
the phone leaves Wi-Fi. You can fill that list for your users: put the nodes that work on
mobile data (for example, through operators' allowlists) in an ordinary group and name it
in a top-level `ducker` key:

```yaml
ducker:
  mobile-group: LTE

proxy-groups:
  - name: Proxy              # the main group: the first select group, shown on Home
    type: select
    proxies: [NL-1, DE-1, LTE-1, LTE-2]
  - name: LTE
    type: fallback           # any type; fallback also suits other apps
    url: https://www.gstatic.com/generate_204
    interval: 300
    hidden: true             # optional: not shown on the Proxy tab
    proxies: [LTE-1, LTE-2]
```

- **The list** is the group's nodes that the main group also has, in your order: ducker
  switches the node of the main group, trying the list from the top and taking the first that
  answers. If none of the nodes is in the main group but the group itself is, the list is the
  group alone, and the group picks its node.
- **When it applies.** A subscription with the key turns "Switch by network" on by itself
  while the user has not set up the page. Settings the user made are not replaced. With
  several such subscriptions, the first downloaded one sets it up.
- **Updates.** While "Provider's list" is on, every update brings your current list. A user
  who edits the list turns it off; turning it on brings your list back. A response without
  the key empties the list and turns switching off; the key coming back turns it on again.
- **`ducker-override-user: on`** turns "Provider's list" and switching back on at every
  update, unless the user chose another subscription for the mobile network. The page is not
  locked: the user can still turn switching off until the next update.
- **If you cannot change the YAML,** send the group's name in the
  [`ducker-mobile-group`](headers.md) header; the group must exist in the config. The key wins
  over the header. Not applied on a TV.

Other apps see an ordinary group and ignore the key. The config is not rewritten.

## Response headers

ducker interprets standard subscription headers as follows:

| Header | Effect |
|---|---|
| `profile-title` | Subscription name. |
| `profile-update-interval` | Subscription update interval, in hours. |
| `subscription-userinfo` | Used traffic, limit, and expiry on Home and the subscription card. |
| `subscription-refill-date` | When the traffic counter resets, as Unix time in seconds. |
| `support-url` | **Support** button; also the **Renew** button if `ducker-buy-url` is absent. |
| `profile-web-page-url` | Provider page; also the **Renew** button if neither `ducker-buy-url` nor `support-url` is set. |
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

`name` is optional: the subscription's name while the provider sends no `profile-title`. Once the
header arrives, its name is shown instead; a name the user gives the subscription comes first.
Without either, the card shows the link's domain.

`mirror` is optional and repeats: each is a backup address written as in
[`ducker-mirrors`](headers.md#subscription-mirrors) — a bare host or a full `https://…` link. They
are tried at this first download when the link is already blocked. After that, the list comes from
the header: a response from the link itself without it removes them. When a user shares the
subscription as a QR code or sends it to a TV, ducker passes its current mirrors along.

`key` is optional: the decryption key of an encrypted subscription, `AGE-SECRET-KEY-…`; it fills
the **Decryption key** field. ducker adds it itself when a user shares an encrypted subscription as a
QR code or sends it to a TV. A link with the key opens the subscription with nothing else, so on a
shared page give the key separately, as above.
