# Questions and answers

**English** · [Русский](FAQ.ru.md)

Help for people who use ducker and are not VPN experts. Buttons and items are named exactly as
in the app.

<!-- For those who edit this page. It is edited together with the Russian version, FAQ.ru.md.
     Button names come from app/src/main/res/values/strings.xml (Russian ones from
     values-ru/strings.xml). The steps for phone makers are the background_steps_* strings; which
     of them to show, the app decides by brand and Android version in
     app/src/main/java/com/github/kr328/clash/util/Background.kt. If a string changes in the app,
     change it here too. -->

- [What a VPN, a subscription and a server are](#what-a-vpn-a-subscription-and-a-server-are)
- [How to add a subscription](#how-to-add-a-subscription)
- [The VPN turns itself off](#the-vpn-turns-itself-off)
- [The VPN did not start](#the-vpn-did-not-start)
- [The subscription does not update](#the-subscription-does-not-update)
- [No internet with the VPN on](#no-internet-with-the-vpn-on)
- [An app complains about the VPN](#an-app-complains-about-the-vpn)
- [Google Play Protect asks to scan the app](#google-play-protect-asks-to-scan-the-app)
- [How to send a log](#how-to-send-a-log)
- [What never to send](#what-never-to-send)

## What a VPN, a subscription and a server are

**A VPN** is a protected channel from your phone to a server. Apps reach the internet not
directly but through that server: sites see it instead of you, and what is closed in your network
opens.

**A server** is the provider's computer, usually in another country. In ducker servers are called
**nodes** and live on the Proxies tab. There are usually several: if one does not work or is slow,
choose another. The number next to a server is its delay in milliseconds (ms): the smaller, the
faster it answers. "no answer" means the server cannot be reached from this network right now.

**A subscription** is your access to the servers. It comes from a provider — the service where
you bought or received it — as a link, a QR code or a file. Using the link, ducker downloads the
list of servers and the settings itself and later updates them itself. The subscription term and
the traffic left are shown on Home.

ducker is only a program for connecting. It does not sell access: without a subscription from a
provider there is nowhere to connect to.

**Global and Rules** — a switch on the Proxies tab, if the provider left it there. Rules means "as
the provider set it up": usually local sites go directly and the rest through the server. Global
means everything through the server. If unsure, leave Rules.

## How to add a subscription

First get a subscription link (it starts with `https://`), a QR code or a file from your provider.

1. Open ducker. While there are no subscriptions, there is one button at the bottom — "Add
   subscription". If you already have subscriptions — the Subscriptions tab, the "+" button at
   the bottom centre, above the tabs.
2. The "Add subscription" sheet opens. Choose how:
   - **A link.** Copy the link from the email or chat, press "Paste" in the sheet, then "Add".
     Server links like `vless://…` work too.
   - **A QR code.** "Scan QR" — and point the camera at the code. If the code came as a picture
     or a screenshot, press "From gallery" in the camera window and pick it. Dark around —
     "Flashlight".
   - **A file.** "File" — and choose the file from your provider. If it contains links, they
     appear in the field: press "Add". If it is a ready config, the subscription is added at once.
3. Wait: the provider is sending the settings, this may take up to a minute.
4. On Home press the big round button with the power sign. The first time ducker warns that
   Android will now ask about VPN: press "Continue" and allow the connection in the Android
   dialog. Android shows this dialog for any VPN. When everything is ready, Home says "Protected".

If a card "The provider’s config has an error…" appears on Home after adding, there is nothing
you need to do: the VPN works. The provider made a mistake in the subscription settings, so ducker
took the servers from it and set up the rest itself. Press "Copy the error" and send it to your
provider: once they fix it, their settings come back with the next subscription update and the
card disappears.

A subscription added from server links (`vless://…` and similar) or from a file with a ready
config does not update itself: its card says "Own config · no updates". If your provider has an
`https://…` subscription link, it is better to add that.

**Move a subscription to another phone** or set it up for a relative. On the phone that already
has the subscription: the Subscriptions tab → the QR icon on the subscription card (for an
inactive subscription — "⋯" → "Share as QR code"). On the other phone: "+" → "Scan QR". A
screenshot of the code works too — through "From gallery". Show the code only to your own
devices: anyone who has it can use your subscription.

## The VPN turns itself off

To save battery, Android and phone makers' software put background apps to sleep and close
them — and the VPN goes off together with ducker. This is set up once and takes a couple of
minutes.

1. **Allow background work.** On Home press ⚙ Settings (top right) → the "Connection" section →
   the "Background work" card → "Allow", and confirm in the Android dialog. When everything is
   fine, the card says: "Android won't put ducker to sleep, so the VPN keeps working with the
   screen off." The card on Home with the "Allow" button offers the same.
2. **Set up your phone maker's software.** Xiaomi, Redmi, POCO, Huawei, Honor, vivo, iQOO, OPPO,
   realme, OnePlus, Samsung, Tecno, Infinix and itel close apps on their own even when Android
   has allowed them. Tap the "Background work" card: ducker shows the steps for your phone and a
   button that opens the right settings. The same steps are below. A card on Home with the "Set
   up" button reminds you about it.
3. **Do not swipe ducker away from recent apps.** Better lock it there — how exactly is written
   in the steps for your phone.
4. **Turn on "Always-on VPN"** so that the VPN comes up as soon as the phone boots: Settings →
   "Always-on VPN" → in the VPN settings that open, press ⚙ next to ducker and turn on always-on
   VPN. The "Autostart" switch in ducker's settings also brings the VPN up after a restart, but
   later — on slow phones in a minute or two.

If the VPN went off when you turned on another VPN app — that is how Android works: only one VPN
runs at a time.

### Steps for your phone

ducker shows the same steps in the "Background work" card. The Android version is in the phone's
settings, in "About phone".

<details>
<summary>Xiaomi, Redmi and POCO</summary>

Tap "MIUI autostart" and turn ducker on in the list. Then "Battery: no restrictions" and choose
No restrictions. And lock ducker in recent apps: pull its card down, or long-press it and tap the
lock.

The "MIUI autostart" and "Battery: no restrictions" buttons are in the "Background work" card in
ducker's settings.

</details>

<details>
<summary>Huawei and Honor</summary>

Open Phone Manager → App launch (on Huawei: Settings → Battery → App launch), find ducker, turn
off automatic management and turn on all three switches. Then lock ducker in recent apps.

</details>

<details>
<summary>vivo and iQOO</summary>

**Android 13 and newer.** In ducker's app settings open Battery and allow unrestricted background
use, then enable ducker in Apps → Autostart. Lock ducker in recent apps.

**Android 12 and older.** In Settings → Battery → Background power consumption allow ducker to
run in the background, then enable ducker in Apps → Autostart (on older versions: i Manager →
Autostart). Lock ducker in recent apps.

ducker's page in the phone settings is opened by the "Open ducker settings" button in the
"Background work" card.

</details>

<details>
<summary>OPPO, realme and OnePlus</summary>

**Android 12 and newer.** In ducker's app settings open Battery usage and allow background
activity: on newer versions choose Allow background activity instead of Smart mode, on earlier
ones turn on that switch and Allow auto launch. Then lock ducker in recent apps.

**OnePlus on Android 11 and older.** In Settings → Battery → Battery optimization choose Don't
optimize for ducker, and turn off Advanced optimization in the extra settings there. Then lock
ducker in recent apps: OnePlus sometimes turns optimization back on by itself.

**OPPO and realme on Android 11 and older.** In ducker's app settings open Battery usage or Power
saver and allow background running and auto launch. Then lock ducker in recent apps.

ducker's page in the phone settings is opened by the "Open ducker settings" button in the
"Background work" card.

</details>

<details>
<summary>Samsung</summary>

**Android 12 and newer.** In ducker's app settings open Battery and choose Unrestricted — if you
allowed background work in ducker's prompt, this is already done. Then in Battery → Background
usage limits make sure ducker is not among sleeping or deep sleeping apps.

**Android 11 and older.** In ducker's app settings open Battery and turn off battery optimization
and putting the app to sleep. Then in Device care → Battery remove ducker from sleeping apps and
turn off putting unused apps to sleep.

ducker's page in the phone settings is opened by the "Open ducker settings" button in the
"Background work" card.

</details>

<details>
<summary>Tecno, Infinix and itel</summary>

**Android 12 and newer.** In ducker's app settings open Battery, turn on Allow background
activity and choose Unrestricted. Then in App management open Auto-start and enable ducker. Lock
ducker in recent apps: on these phones swiping it away stops the VPN.

**Android 11 and older.** Open Phone Master → Auto-start and allow ducker, then turn off power
saving for apps in Settings → Battery. Lock ducker in recent apps.

ducker's page in the phone settings is opened by the "Open ducker settings" button in the
"Background work" card.

</details>

## The VPN did not start

If the VPN could not be turned on, a red card "VPN didn’t start" appears on Home: it gives the
reason, what to do about it and the right button — "Update", "Support" or "VPN settings". If you
turned the VPN on with the Quick Settings tile or a widget without opening the app, the same
arrives as a notification; tap it. The card disappears by itself when the VPN comes up.

## The subscription does not update

A subscription added by a link updates itself — usually every 8 hours. How often is shown and
changed here: Subscriptions → "Edit" → "Auto-update".

1. **Update by hand:** Subscriptions → "Update" on the subscription card. The result appears
   right on the button: "Updated" or "Failed". After a successful update the subscription goes
   back to updating itself on schedule.
2. **Read the reason.** On an error you see "Could not update “…”", and below it — why, and a
   button with what will help: "Update", "Edit" or "Support".
3. **Turn the VPN off and update again.** While the VPN is on, the subscription request usually
   goes through a server; if the servers do not answer, updating is impossible. Without the VPN
   the request goes directly.
4. **"Device limit reached"** — the provider counts devices and the limit is used up. Remove an
   old device at the provider (in your account or bot) or write to them with the "Support"
   button, then press "Update". Also check Settings → "Device ID": it must be on. Without it a
   provider that counts devices will not hand out the subscription.
5. **"Not loaded"** — the provider did not answer or the link is wrong. If the provider sent a
   new link: "Edit" → paste it into the "Link" field → "Save".
6. **"Expired"** — the subscription term is over. Renew it with the "Renew" button (if the
   provider added it) or at the provider, then press "Update".
7. **"Own config · no updates"** — the subscription was added from server links or from a file
   with a config, so there is nowhere to update it from. Ask your provider for an `https://…`
   subscription link and add that.
8. **"The config was edited by hand"** — an update will replace your edits with the provider's
   settings. If you do not need the edits, press "Update anyway".

## No internet with the VPN on

First look at Home: ducker reports the most common causes there itself.

- **"The node is not answering"** — the VPN is on, but the server cannot be reached. Press
  "Switch to the fastest" or "Choose another node". If it says "No node answered", this network
  blocks the servers: try another network — Wi-Fi or mobile data.
- **"Expired"** or little traffic left on the subscription card — renew the subscription.
- **"Device limit reached"** — see item 4 [above](#the-subscription-does-not-update).
- **"Block connections without VPN"** — the block is turned on in Android settings, and apps that
  bypass the VPN have no internet at all. If you need them, press "VPN settings" and turn the
  block off.

If there are no such cards:

1. Turn the VPN off and on again with the big button on Home.
2. Update the subscription: Subscriptions → "Update". The provider may have changed the servers.
3. On the Proxies tab press "Check" and tap a server that shows a number rather than "no answer".
4. Try another network: Wi-Fi instead of mobile data, or the other way round.
5. Make sure the date and time on the phone are set automatically: with a wrong time a protected
   connection to the server may fail to establish.
6. If you turned on "IPv6" (Settings → "Network and tunnel"), turn it off: it is needed only if
   the provider supports it.
7. One app does not work while the others do — open Settings → "Apps" and check whether it goes
   through the VPN. A provider may deliberately send some apps, banks for example, past the VPN.
8. Did not help — [send a log](#how-to-send-a-log) to your provider's support.

## An app complains about the VPN

Some apps — banks, government services, marketplaces — say they will not work with a VPN on, or
simply do not open. Such apps can be sent past the VPN without turning it off for everything else.

1. Settings → "Connection" → "Apps".
2. At the top choose "Bypass VPN" — the checked apps will go past the VPN.
3. Find the app with the search ("Search by name or package") and check it.
4. Go back. If the VPN is on, ducker restarts it itself so that the choice takes effect: the app
   list is applied only when the VPN is turned on.

The provider already sends some apps past the VPN itself: they are at the top of the list with a
lock and the label "no VPN", and cannot be unchecked — this is how banks and government services
work with the VPN on.

If "Block connections without VPN" is turned on in Android settings, apps that bypass the VPN have
**no internet at all** — ducker warns about this on Home. If you need such apps, press "VPN
settings" there and turn the block off.

The "Via VPN" choice works the other way round: only the checked apps go through the VPN, the
rest go past it. While nothing is checked, all apps go through the VPN.

<!-- Sources: the strings access_control, apps_mode_*, apps_profile_exclude, apps_locked_direct,
     lockdown_offline; the screen ui/apps/AppsScreen.kt; the restart after a change —
     HomeActivity.applyAppFilter (the list is set when the tunnel is created); the provider's
     list cannot be unchecked; no internet in lockdown mode — measured on a phone. -->

## Google Play Protect asks to scan the app

When you install or update ducker, a "Google Play Protect" window may appear with the title
"App scan recommended". Google shows it for any file it has not seen yet: ducker is not installed
from Google Play, and every new version is a new file to Google.

1. Tap "Scan app". Google receives details of the app and answers within a few seconds.
2. When it reports that the app looks safe, confirm the installation in the same window.

"Don't install app" cancels the installation and breaks nothing: when updating, ducker shows the
"Update" button again. If Google answers differently, do not install; write to us through
⚙ Settings → the "Help" section.

<!-- The window's labels come from the Google Play Store, not from the app: recorded on a phone on
     12.10.2026. In ducker the button is the string update. -->

## How to send a log

The log is a record of what happened to ducker and the VPN. Support uses it to find the cause.

1. Right after the failure open ⚙ Settings → the "Help" section → "Log".
2. If the failure can be repeated, turn on "Detailed log", repeat it, and then turn the detailed
   log off: it uses battery. If you forget, it turns itself off in a day.
3. Press "Send" and choose a messenger or email. You can also "Copy" it and paste it into a
   message, or "Save" it to a file.

ducker itself sends nothing anywhere: the log leaves the phone only when you press one of these
buttons, and only to where you choose.

Where to send it: to your provider's support — the "Support" button on Home, if the provider
added it. A bug in the app itself — to the ducker developers, as described in
[CONTRIBUTING.md](../CONTRIBUTING.md).

What is inside: the phone model and Android version, battery settings, the network type, why
Android closed the app, and ducker's records for the last hours — even if the Log screen was
closed at the time. ducker hides server addresses, passwords and keys, the key from the
subscription link and the device ID itself. **The addresses of the sites you opened stay**: they
are used to work out what went through the VPN and what went past it. If you do not want to show
them, look through the log before sending.

**The "The app crashed" screen.** If ducker closed with an error and showed this screen, copy the
report and send it to support. Server addresses, keys and the device ID are removed from it the
same way as from the log.

## What never to send

To no one — not to support, not to chats, not to GitHub:

- **Your subscription link and its QR code** — including a screenshot of the "Share as QR code"
  screen. Anyone who has them can use your subscription.
- **Server links** like `vless://`, `trojan://`, `ss://` — they contain passwords.
- **The config** — the subscription's settings file or a screenshot of the "Config" screen: it
  holds server addresses and passwords. If support asks for the config, send a copy without
  them: Subscriptions → "Edit" → "Config" → "Save as" → "As a file without servers". The copy has
  no servers, passwords or subscription links; groups, rules and DNS are in place. Look through
  the file before sending.
- **A screenshot of the log.** On the screen the log is shown in full: ducker cleans it only on
  "Send", "Copy" and "Save".

**The device ID** — only to your own provider's support, and only if they ask for it.
