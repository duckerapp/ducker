# Changelog

What changed in each version of ducker. [Русская версия](docs/CHANGELOG.ru.md).

The versions below were built for testers before the repository was opened; they are listed here
for history. APK files are on the [Releases](https://github.com/duckerapp/ducker/releases) page.

## 0.4.35 — 2026-10-20

- Automatic updates: with the first new version, the update sheet asks once whether ducker should
  update itself. If yes, new versions download over Wi-Fi and install while the screen is off,
  usually at night, and the VPN comes back by itself. The switch is in Settings → Information.
  Android 12 and newer; it starts working once releases are published.
- "What's new" in the update sheet sits on a card, like the rest of the new look.

## 0.4.34 — 2026-10-19

- A new look: soft shadows instead of outlines, the Onest font and Material Symbols icons throughout.
  The dark theme is a step lighter; with the black background, cards keep thin edges.
- Nine new languages: Chinese (Simplified), Farsi, Arabic, Spanish, French, Indonesian, Portuguese
  (Brazil), Turkish and Vietnamese. Traditional Chinese, Japanese and Korean now show English.
- Home: a quick node choice in a sheet with search, sort and the delay check; the provider's
  announcement as a short preview with the full text a tap away; "Connected" as the status; a more
  compact layout on shorter screens.
- Settings are grouped into Interface, VPN and Information, each section on its own page; the
  language is picked from a list; a new "Support the project" page; beta versions sit next to the
  update check, and "Control from other apps" is under Privacy.
- The English tab is now called Profiles. Every subscription is its own card, with one action menu
  for all of them.
- Pull to refresh on Proxies and Profiles works only from the very top, and the circle no longer
  hangs.
- Less memory used while cards in Settings and on Profiles expand.

## 0.4.33 — 2026-10-14

- Updates from inside the app: ducker checks for a new version, shows it on Home with what's new,
  downloads it with progress and installs it. It starts working once releases are published.
- Subscriptions: a new layout — the active subscription as a card on top, the others as rows that
  can be dragged into order. Auto-update can follow the interval the provider sets.
- Proxies: delays appear while a check is still running, a node shows «no answer» right after its
  check, and a delay at the time limit reads «> 5 s». Nodes left with «no answer» after the VPN
  starts, after a change of network or after a while are checked again by themselves. The sort
  switch is there with the VPN off too.
- An update of a subscription no longer breaks open connections; switching to another subscription
  closes those left on the previous one.
- «Renew» leads to the provider's support when the provider has no renewal link.
- The log follows new lines, with a button back to the end, and keeps running while the app is
  hidden. The device ID is hidden until tapped.
- The first run opens on the welcome screen without flashes.
- System colors tint the app with the wallpaper's own color; smoother sheets and lists.
- Settings → Help: the project's email.
- Fixed: a large list of links pasted at once failed to be added.

## 0.4.32 — 2026-10-07

- A new subscription whose config has a mistake by the provider is no longer refused: ducker
  takes the nodes from it, runs on its built-in settings and shows a card on Home with the error
  to pass on to the provider. Once the provider fixes it, their config comes back with the next
  update. A subscription that already works keeps its previous config, as before.
- Settings → Help: links to the Telegram channel and the community chat.

## 0.4.31 — 2026-10-06

- The core is updated to mihomo 1.19.32. Our additions — mux.cool, REALITY as in Xray, the check
  that a multiplexed connection is still alive — are carried over unchanged.
- For providers: `tun.congestion-controller` from the profile now works; before, it was silently
  lost. It applies to the `mips` stack only.

## 0.4.30 — 2026-10-06

- Slightly faster start with the VPN on: the list of code that Android prepares in advance is
  refreshed.

## 0.4.29 — 2026-10-06

- Faster start with the VPN off: Home shows the node and its delay at once, without grey
  placeholders. About one sixth shorter on a slow phone.
- No white flash at launch when ducker uses the dark theme and the phone the light one, or the
  other way round (Android 12 and newer).
- A subscription made of a list of links is added in a second instead of six seconds or more.
- With the VPN on, screens other than Home are no longer redrawn every second.
- My rules: a country rule warns that sites open a little slower the first time.

## 0.4.28 — 2026-10-06

- When the VPN does not start, a card on Home says why in plain words and offers what to do:
  Update, Support or VPN settings. A notification says the same when the app is closed.
- Subscription errors in words — "There is no subscription at this link", "The subscription has no
  nodes" — instead of raw core messages.
- The config editor keeps an unsaved edit if Android unloads the app, and offers to restore it.
- The new duck in the notification and on the Quick Settings tile; a new banner on Android TV.
- Home and the tile show "Protected" correctly while the subscription is still loading.
- A VPN that failed to come up no longer stays "on" in the system without a connection.
- "Detailed log" turns itself off after a day.
- The subscription term on Home is recalculated every time you return to the app.
- The app list in Apps and My rules is built once and opens instantly the second time.
- Provider logos and group icons are loaded only from the internet or sent as the image itself; an
  address that points to files on the phone is not opened.

## 0.4.27 — 2026-10-05

- A new app icon: the duck peeks over the edge.

## 0.4.26 — 2026-10-05

- Texts in the app are shorter and clearer, in Russian and in English.
- Speed in the notification is off by default.
- Commands from other apps (Tasker and similar) are off until you allow them in Settings →
  Control from other apps. The tile, widgets and shortcuts work as before.
- The "VPN mode" switch is gone: the app always brings up a regular VPN.
- A provider's "add subscription" link opens the app, shows where the subscription comes from and
  downloads it only after you press Add.
- If an updated subscription brings a broken config, the VPN keeps working on the previous one.
- Apps, Log and Node test are no longer empty after Android unloads the app.
- Battery: nothing runs in the background while the VPN is off; the app no longer wakes up on
  every VPN event when there are no widgets on the home screen.
- Privacy: the device ID is sent to the provider only over `https://`; subscription configs are
  no longer visible in the system file picker; the built-in template no longer opens port 7890;
  certificates installed on the phone by hand are not trusted; passwords and keys inside server
  links are removed from the log report.
- For providers: the `ducker-local-proxy: off` header brings the VPN up with the config's local
  ports closed.

## 0.4.25 — 2026-10-05

- Android TV: the app is controlled with the remote. Tabs are on the left, a frame shows the
  focused element.
- A subscription is sent from the phone to the TV by QR code, encrypted, over the home network.

## 0.4.24 — 2026-09-28

- A more honest "Connected": if the node does not answer, the "no connection" card appears in
  about 20 seconds instead of more than a minute, with a "Switch to the fastest" button.
- An explanation before Android's VPN request, shown once.
- A subscription can be shared as a QR code.
- Notifications 7, 3 and 1 day before the subscription ends, with a Renew button.
- "Always-on VPN" in Settings leads to the Android screen where it is turned on.
- New widgets: black Nothing-style button and switch, and a white switch.
- Scheduled subscription updates arrive on time.
- Large system font: button labels shrink instead of being cut off. TalkBack: the power button,
  input fields and switches have names.

## 0.4.18 – 0.4.23 — 2026-09-28

- Home screen widgets: a button and a switch in ducker and Nothing styles, and a Nodes widget
  that switches the node with a tap.
- Disguise as a calculator: ducker becomes "Calculator" on the home screen.
- Launcher shortcuts and Tasker commands only turn the VPN on and off, without opening the app.
- The Global/Rules mode is kept separately for each subscription.
- Config editor: edits survive screen rotation.
- Node server addresses are hidden in the exported log.

## 0.4.16 – 0.4.17 — 2026-09-28

- Core: REALITY works with new Xray servers; mux.cool support; a broken node in a subscription is
  skipped instead of breaking the whole subscription.
- Proxies: SVG group icons, search by protocol, a Global mode switch only when the provider
  enables it.
- Subscriptions: adding from a file and from a QR code in a screenshot, reordering by dragging,
  a clear result after Update.
- Apps: two modes, "Bypass VPN" and "Via VPN".
- "Hide icon" is removed: Android 10 and newer does not let an app hide its icon anyway.

## 0.4.15 — 2026-09-25

- The VPN turns itself back on after a phone restart and after an app update.
- Xiaomi, Redmi and POCO: buttons that open the Autostart and Battery screens directly.
- Smoother on slow phones: the node list and tabs scroll without jerks.

## 0.4.14 — 2026-09-24

- Config editor: highlighting, search and replace, undo and redo, and a check by the same core
  that runs the VPN.
- "Save as": a new subscription, a file, or a file without servers — to share a config without
  showing addresses and passwords.
- `.yaml` files open in ducker.
- My rules: a word in the address, a site category and a country, in addition to a site, an app
  and an address.

## 0.4.13 — 2026-09-24

- Quick node switch on Home: tap the node row under the button.
- My rules: send a site, an app or an address past the VPN, through it, or block it. Rules survive
  subscription updates.
- The subscription card on Home shows Support and, when the subscription is about to end, Renew.
- Settings are grouped into sections.

## 0.4.6 – 0.4.12 — 2026-09-23

- The VPN recovers faster after the phone sleeps and when switching between Wi-Fi and mobile
  data: a dead connection to the server is noticed in a couple of seconds, not in 10–30.
- ducker asks for permission to work in the background and explains why. Step-by-step
  instructions for phones that close apps in the background: Xiaomi, Huawei, Honor, vivo, OPPO,
  realme, OnePlus, Samsung, Tecno, Infinix and itel.
- After the VPN is turned on, automatic node selection measures delays again and takes the
  fastest node.
- The log is always written, and one tap on Send shares it.
- Fixed: "no answer" shown for a working node right after connecting.

## 0.4.1 – 0.4.5 — 2026-09-23

- A new icon: a white duck on a black tile, with a themed-icon version.
- First launch: a welcome screen with one big "Add subscription" button instead of empty tabs.
- Adding and editing a subscription happens in a bottom sheet; the subscription card shows a
  traffic bar and Update, Edit and Delete buttons.
- The device ID moved to the bottom of Settings, so that it is not turned off by accident.
- Fixed: a subscription failed to add with a "TLS handshake" error after a broken one.

## 0.4.0 — 2026-09-23

- The power button is redesigned: the new state spreads from the button as a wave.
- The back gesture follows the finger; lists on Proxies and Subscriptions can be pulled down to
  refresh.
- The tunnel comes back up after an app update and after a crash.
- The home network works with Android's "Block connections without VPN" turned on.
- The QR scanner no longer uses Google services and sends nothing to them.
- A Quick Settings tile is added with one button; launcher shortcuts "Connect" and "Disconnect".
- The app stopped loading the phone for nothing while it is open.

## 0.3.0 — 2026-09-22

- A fresh build base: AGP 9.4, Gradle 9.7, Kotlin 2.4, Compose 1.12.

## 0.2.0 — 2026-09-22

The first build for testers.

- A black-and-white interface written from scratch in Compose: a new Home, tabs that swipe, a
  theme that changes as a wave.
- Favorite nodes, node delays before connecting, node search.
- The notification shows the provider, the node and a Disconnect button.
- A Quick Settings tile, the built-in web panel, `ducker://` links.
- Provider branding with the `ducker-brand-name` header.
