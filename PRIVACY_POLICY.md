# ducker privacy policy

[Русский](docs/PRIVACY_POLICY.ru.md)

## Overview

- ducker does not collect analytics, show ads, or send logs or crash reports to the developer.
  The project has no server of its own to receive such data.
- Network requests are limited to what the VPN needs: requesting the subscription from your
  provider, sending your traffic through its servers, and core requests specified by the
  profile. The app also checks whether a new ducker version is available. Details follow.
- Everything else is stored only on the phone and removed when the app is uninstalled.

## What goes to the network and who receives it

**1. Your subscription provider** — when you add or update a subscription. The request uses
the URL you supplied and includes:
- a `User-Agent` such as `mihomo/<core version> ducker/<app version>`;
- if **Settings → Device ID** is enabled (the default): the device id, the word `Android`,
  the Android version, and the phone's manufacturer and model. The id is `ANDROID_ID`:
  Android assigns it separately to each app on the phone, and other apps cannot see it.
  If it is unavailable, ducker generates a random id once. The provider uses it to count
  your devices. You can see the id being sent in the same settings section. Disabling
  this switch may prevent a provider that counts devices from returning the subscription.

Like any server, the provider sees your IP address — or its node's address if the VPN is
enabled and the request travels through it.

An external `ducker://install-config` link (also `clash://` or `clashmeta://`) first shows
the source and asks for confirmation. The profile is not downloaded before you tap **Add**;
cancelling does not create a subscription.

Device data is sent over HTTPS only; an `http://` URL receives only the User-Agent. HTTP
leaves the URL and profile visible to the network, so the app warns before you add or edit
such a URL. The provider decides where the URL leads: the app has no allowlist of addresses.

If the provider's server redirects the request, the app follows up to ten redirects.
Device data follows while the entire chain remains HTTPS. After the first `http://` address,
device data is no longer sent, even if a later redirect returns to HTTPS.

**2. Subscription images.** Group icons and the provider's logo are downloaded from
addresses specified by the provider.

**3. Your traffic.** While the VPN is enabled, app traffic travels through the provider's
servers (nodes), as with any VPN. The provider and its nodes see what any VPN service can
see; ducker does not change this.

**4. Core service requests.** These are specified by the provider's profile or the built-in
template:
- node latency checks: requests through the node being checked to an address in the profile,
  usually `www.gstatic.com/generate_204`; the app's connection check uses
  `cp.cloudflare.com/generate_204` through the node;
- time synchronization (NTP), if enabled by the profile (`pool.ntp.org` in the built-in template);
- rule sets and address databases referenced by the profile.

**5. Links you tap.** The provider's page, support, payment, and ducker release links open
in your browser only when you tap them.

**6. Your TV, only at your request.** When you scan the TV's ducker QR code in the phone app
and confirm sending, the phone sends the subscription URL to that TV over your home network.
The URL is encrypted with a one-time key from the QR code. The TV listens on the network
only while the code is displayed.

**7. Checking for a new ducker version.** About twice a day and when opened, the app downloads
`update.json` from the project's GitHub repository, or from its GitLab mirror if GitHub does
not respond. The request contains no information about you or the phone: only a `User-Agent`
such as `ducker/<app version>`. The new version itself is downloaded from the same sites,
only after you tap **Update**. Like any server, GitHub and GitLab see your IP address — or the
provider node's address if the VPN is enabled. Before installation, the app checks the file's
checksum and signature: it will not install a file from someone else.

## What stays on the phone

- subscriptions: URLs and downloaded configs;
- app settings and your rules;
- node latency measurements;
- the service log: two rotating files of 256 KB each;
- crash records: two files of up to 64 KB per process, plus the local core error log;
- cached subscription images.

All of this is removed when the app is uninstalled.

**Android backup.** Only settings are included in cloud backups and transfers to a new phone.
Subscriptions and configs are excluded because they contain access keys to your VPN.

## Logs

Logs stay on the phone. They leave it only when you tap **Copy**, **Send**, or **Save** under
**Settings → Log**. Node addresses, keys, and the device id are removed first. The domains
of sites you visited remain because they are needed to understand how rules worked.
The screen warns about this, and you can review the text before sending. Kotlin crash
records and the tail of core errors are scrubbed in the same way; the crash screen displays
and copies only the prepared report. Nothing is sent automatically.

## Permissions and why they are needed

- **VPN** — to route traffic through the tunnel.
- **Notifications** — to show that the VPN is running and remind you when a subscription expires.
- **Camera** — only to scan subscription QR codes. Frames are neither saved nor sent anywhere.
- **App list** — to let you choose apps that bypass the VPN. The list stays on the phone.
- **Background work and start after reboot** — to prevent the VPN from stopping on its own.
- **`INTERACT_ACROSS_USERS`** is declared in the manifest but can be granted only manually
  through USB debugging (adb). The app does not request it itself.

## Changes

This policy changes with the app. Its change history is in this file's repository history.

## Questions

Use repository [Issues](../../issues) or email [duckermihomo@proton.me](mailto:duckermihomo@proton.me).
Report vulnerabilities according to [SECURITY.md](SECURITY.md).
