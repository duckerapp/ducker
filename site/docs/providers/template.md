# Configuration templates

The default **From the subscription** mode uses a valid provider config as supplied.
Users may explicitly choose **ducker · Russia** under **Profiles → ⋯ → Configuration**
for URL subscriptions. That choice takes server connections and `proxy-providers` from
the subscription; groups, rules, DNS and app exclusions come from the template. Provider
health-check settings and node dependencies are retained; the template adds its own checks.
The provider's `ducker-override-user` does not lock the template's app rows in this mode.
Encrypted subscriptions cannot use the explicit template mode.

The switch replaces manual config edits. The new mode takes effect once it is applied.
If the switch fails, the previous working config stays active. Choose **From the
subscription** to restore provider routing. See [user instructions](../users/faq.md#configuration-templates).

When a subscription contains a plain list of node links, ducker puts them into the built-in template automatically.
It can also recover nodes from a broken config for a new subscription, with a visible
error card. An existing working subscription keeps its old config after a failed update.

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

## If you need your own routing

Provide a complete mihomo config as described under [Subscription for ducker](start.md).
In the default source mode that config is used. A user can still explicitly choose the template.
