# Changelog

What changed in each version of ducker. [Русская версия](docs/CHANGELOG.ru.md).

1.0.0 is the first public release. The versions before it were built for testers while the
repository was closed; they are listed here for history. APK files are on the
[Releases](https://github.com/duckerapp/ducker/releases) page.

## 1.0.0 — draft
<!-- Draft until the 1.0.0 build: set the date then. Keep every line inside the list: publish.sh
     joins any other line to the item above it, and the in-app "What's new" shows the result. -->

- The first public release: the source, the releases and the updates are open. Updates from inside the app now
  work for everyone, from GitHub or, when it does not answer, from a mirror on GitLab or jsDelivr.
- Encrypted subscriptions: when a provider encrypts its subscription, the add sheet says so and shows a “Decryption
  key” field for the key the provider gives you. Editing the subscription keeps the key.
- The subscription request names the platform: `mihomo/<core> ducker/<app> (android)`.
- A provider's broken or oversized headers no longer reach the screen: a huge announcement or name is ignored, a
  value that fails to decode counts as not sent, and accented Latin text such as “Café” shows correctly.
- A provider's logo or group icon larger than 2 MB, or one that takes over 30 seconds, is skipped instead of filling
  the phone's memory.
- PasarGuard: its device limit shows the device-limit screen instead of a general error, and the subscription details
  it puts at the end of the file are read.
- Rule sets and node lists that a config downloads are requested with the headers the config sets for them from the
  very first download, so a provider's token in them works at once.
- A phone or TV whose model name contains unusual characters no longer fails every subscription request.
- TV: “Features” is a round button under the dock, so it always fits on the screen.
- With Android's “Block connections without VPN” on, an app from the provider's exclusion list or from your choice
  in “Apps”, installed while the VPN is on, takes its place at once — past the tunnel or through it — instead of at
  the next connect. Open connections reconnect once at that moment.
- Turning the VPN off while it is still connecting, or while the subscription reloads, no longer leaves the
  subscription running with the VPN off: its local ports open, its node checks and updates going out directly.
- The built-in template no longer opens a DNS server on `127.0.0.1:1053`: apps kept out of the VPN could find ducker
  through it and send queries through the server. A subscription from a list of links drops it at its next update.
  For providers: the `ducker-local-proxy: off` header closes a config's DNS server (`dns.listen`) too.
- “Wi-Fi sharing”: “New password” makes another password at once, and devices with the old one are let go. With the
  password on, the card says that only a browser asks for it. The card says the second device joins on the same
  Wi-Fi or on the phone's hotspot, and turning sharing on or off no longer moves the icon and the switch.

## 0.4.47 — 2026-11-09

- When the phone closes ducker in the background, the notice now says so — “Your phone closed ducker in the
  background” — and Home offers “Background work”, instead of blaming an error in ducker.
- On Xiaomi phones “Background work” shows whether MIUI's autostart is on for ducker, and Home stops suggesting the
  Xiaomi steps once it is.
- After ducker updates itself, the VPN that was on comes back as soon as ducker starts — the app, the widget or the
  tile — without waiting for the phone.
- “Update” on a subscription no longer ends in “No response” on slow phones without trying: the button, “Update” in
  the notification and the update by schedule start at once. A subscription with backup links gets more time before
  “No response”.
- The nodes widget switches the server at once and follows the VPN without delay on slow phones; the confirmation of
  an app update no longer waits there either.
- The update error on “Subscriptions” disappears once the subscription has updated, also when that happened while
  ducker was in the background; “Proxies” and “Subscriptions” show what changed while you were away.
- A subscription download cut short by the VPN coming up or going down is tried again.
- The app's own connections stay outside the VPN from its very first start, so the subscription and its rule sets
  download while the VPN is starting.

## 0.4.46 — 2026-11-06

- “Mobile network” in “Features”: a subscription and servers of its own for when the phone leaves Wi-Fi. Turn on
  “Switch by network”, choose the subscription and, if you like, servers in order — the first one that answers is
  used; back on Wi-Fi everything is as usual. A server or subscription you pick yourself stays until the network
  changes again. The network has to hold for about a minute, or a few seconds with the screen on.
- The subscription chosen for the mobile network downloads its rule sets in advance, so the first switch off Wi-Fi
  does not pause your connections.
- The switch waits until the VPN has loaded the subscription and tries again if it failed, instead of reporting a
  switch that did not happen.
- A subscription with VPN settings of its own (app lists, excluded addresses) rebuilds the VPN with them instead of
  running on the previous subscription's.
- Turning the VPN off while a subscription loads no longer hangs.
- A failure in one part of the VPN no longer takes the whole VPN down.
- The app's own connections no longer slip into the VPN for a moment when it rebuilds its interface.
- Choosing servers for a subscription that is not running lists all of them, including those its groups add by
  themselves.

## 0.4.45 — 2026-11-05

- “Features” on Home, under the subscription card, with two tools:
  - “Connections”: which apps connect where, live — the app's icon, the site, the rule and the node, the speed. Tap a
    card to keep only that app, again for only that site; there is a search and “Reset all”, and closed connections
    stay greyed out as “Closed”.
  - “Wi-Fi sharing”: the VPN's internet for a laptop or another phone. Turn it on and enter the IP and the port from the
    card — on someone else's Wi-Fi also the login and the password — in the second device's Wi-Fi proxy settings.
    Through the phone's own hotspot no password is needed. It turns itself off when the network changes or the VPN
    stops; only apps that support a proxy go through it.
- “Protect subscription” (Settings → Privacy): the subscription link, its QR code, sending it to a TV and the config
  editor open only after your fingerprint, face or PIN, and those screens can't be captured. Off by default.
- A bell on “Profiles”: providers' announcements of the last 10 days, the new ones marked “New”.
- A warning when 80% and 95% of the subscription's traffic is used.
- “Add subscription” fills in a link from the clipboard.
- The “Could not update the subscription” notification has an “Update” button.
- Something is hidden on Home.
- “Proxies” checks its nodes only while it is open, and a node shared by several groups once; its list no longer slows
  the screen down and shows at once after the app was closed.
- After the VPN starts, Home checks its own node first: the delay under the button comes sooner, without a false
  “no answer”.
- A full phone is named as such — “Your phone is out of storage” — instead of blaming the provider, and no longer
  crashes the app at every start.
- A subscription is no longer damaged when the app is closed in the middle of its update, and a failed update no
  longer takes the VPN down.
- When the VPN went off after repeated crashes, the app no longer shows it as connected and sends “VPN turned off” with
  “Connect”.
- Under the largest system font labels shrink instead of being cut, a long subscription name takes up to three lines,
  and Home's theme button is bigger.
- Persian and Arabic: the screens are mirrored, while links, addresses and the log read left to right.
- On phones with three buttons at the bottom, lists fade out under the bar instead of the system's dark strip.

## 0.4.44 — 2026-10-27

- On a phone the round “+” no longer covers the subscription cards: a moment after you open “Profiles”, the tab
  itself turns into “+ Add”, with a light tick and a shine; tap it to add a subscription. The TV keeps its “+”.
- Under a large system font: the Home card keeps one height whether the VPN is on or off; the active subscription's
  date and the values in the node test move under their neighbours when both do not fit; a group name on two lines
  in “Proxies” sits closer together.
- The node test's report for support is in the app's language.
- In the add and edit sheets the core's own text of a failure is small and grey under the explanation.
- The QR hints name the “Profiles” tab and “Add”.

## 0.4.43 — 2026-10-27

- TV: its own launch screen, the duck and “ducker”. The banner in the app grid is in the app's font and sharp up to
  4K; the icon tile has rounded corners.
- TV: sheets (“+”, editing a subscription, language and others) stand on the bottom edge of the screen, as on a
  phone; their rows scroll to the edge and do not shake when the keyboard opens, and a sheet raised over the
  keyboard leaves no gap under it.
- TV: the on-screen keyboard lies over the app and the dock stays whole; “Done” closes it and moves on (“Add”,
  “Save”, the first node found).
- TV: the config editor is full width with long lines wrapped; OK on the code places the cursor, the arrows move it,
  another OK opens the keyboard, and text goes in at the cursor; while the keyboard is open, the code shrinks above it.
- TV: the log is full width with long lines wrapped, the remote scrolls it, and “Copy” is in place.
- TV: the cards on “Home”, “Proxies” and “Profiles” stand equally far from the dock and from the right edge.
- TV: “Home” with a provider's announcement fits whole, the subscription card no longer slides off the screen;
  “Read more” opens the whole announcement, and a long text scrolls with the remote up and down.
- TV: on “Profiles” the remote reaches “Update” in an error card, and Left from the cross goes back to it.
- TV, the remote: the frame does not slip into the dock from a group on “Proxies”; the search on “Proxies” takes the
  frame; the list in “Apps” no longer gets stuck on apps the config locks; the “Own” slider opens on OK and the frame
  goes round it whole; Back from a screen opened from the settings returns to its row; OK on a node of a group that
  picks its node itself says so.
- TV: hints say “press” rather than “tap” and speak of the TV, not the phone; there is no quick settings tile, speed
  in the notification, “Disguise as a calculator” or zashboard, which a TV has no use for.
- TV: fixed a crash when an element left the screen while nothing had the focus.
- A long provider name in the header gets smaller and, if it still does not fit, wraps to a second line at a space,
  never inside a word.
- A provider name in a broken encoding (boxes) is not shown: “DUCKER” stands in its place.
- The duck on the subscription card and the app icons are sharp.
- A subscription added from a link becomes the selected one even if the app was unloaded while it downloaded.
- “Copy” in the log takes the newest lines, as many as the clipboard holds.

## 0.4.42 — 2026-10-26

- TV: the theme is dark by default even when the settings came back from a phone's backup with the light one; a
  theme chosen on the TV itself is kept for the TV.

## 0.4.41 — 2026-10-26

- A subscription's menu has “Send to TV”: it opens the scanner for the code a TV shows under “Add subscription”.
- Starring a node on “Proxies” no longer reshuffles the list: the cards change in place.
- The theme switch: the wave reveals the whole screen evenly, with no band of the new theme ahead of it; a quick
  double tap switches once.
- The power button: its fill follows quick presses instead of vanishing, the button fades to its new colour while
  the wave leaves its edge, and on a phone a tap makes it dip and spring back, as a remote's OK does on a TV.
- The duck on the welcome screen is sharp.
- TV: Home fits the screen whole; the card about a node that does not answer puts its text beside its three buttons.
- TV: the name above the dock is larger; the dock and its buttons wear the cards' soft shadow; sheets float inside
  the screen's safe area; scrolled lists run to the screen's edge.
- TV: a TV that reports a low screen density gets the standard TV layout instead of small screens with wide margins.
- TV remote: the frame stays on the config editor in full screen and in search, and Back while editing takes it to
  the top-left arrow; it follows a button that a card pushes down; it never goes to a page or a tab hidden from
  view; the node panel takes it each time it opens; OK held down acts once; Left leaves a page for the dock unless
  something is to the left; marking an app in “My rules” moves on to “Add”.
- TV: Back on the first tab asks “Close ducker?” and says whether the VPN keeps working.
- TV: no background-work prompts, which have nothing to open there; the “+” sheet keeps its code while it closes.

## 0.4.40 — 2026-10-22

- While ducker tries a backup address of a subscription, the add and edit sheets say so: “Trying a backup
  address…”.
- If no address answers, ducker tries each backup address directly, bypassing the VPN, not only the first one:
  the subscription updates even when the node is down and the main address is blocked.
- A provider on plain hosting, without its own headers, can list backup addresses as `#fallback-url:` lines at
  the top of the subscription file, as in Happ.
- The duck on the subscription card no longer keeps the previous theme's look after the theme changes, and has
  no grey rim along its edge.

## 0.4.39 — 2026-10-22

- Subscription mirrors: when a subscription's address is blocked or its server is down, ducker tries the
  provider's backup addresses (`ducker-mirrors`, or Happ's `fallback-url`) on its own and remembers the one
  that answered. Background updates work the same way, with the app closed.
- If no address answers, ducker tries once more directly, bypassing the VPN, and through the subscription's
  own node.
- A provider can move a subscription to a new address (Happ's `new-url` or `new-domain`): the link changes
  only after the new address has given a working subscription.
- The active subscription card shows the subscription's own rules and the provider's backup addresses under
  its name; a tap opens them. The traffic and the term are on one line above the bar.
- “Subscription addresses” lists each address with the result of its last try: answered, no connection, no
  answer in time, a server error, a web page instead of the subscription, or not needed.
- A web page coming instead of a subscription now gets its own explanation instead of a config error.
- When a crash turns the VPN off, a notification says so, with a Connect button.

## 0.4.38 — 2026-10-21

- The core is updated to mihomo v1.19.33: connecting to a DNS-over-HTTPS server no longer waits without
  limit, and a node that answers a delay check in under a millisecond is no longer shown as failed.
- The log now shows a node that stops working: once a minute per node, how many of its connections got
  no answer and the last site among them; a node that refuses a multiplexed connection is logged with
  the server's reason. These lines are kept even with the detailed log off.
- The detailed log costs the phone about two and a half times less work per line.
- The zashboard web panel keeps its settings between visits, no longer grows by a few megabytes with
  each one, and no longer turns into a white page when the phone is rotated. The connection globe is gone
  from it.
- The splash screen shows the new duck.

## 0.4.37 — 2026-10-20

- On Android 10–12 the chosen language no longer falls back to the system one after the app is closed.
- The core is built with Go 1.27.3, which brings security fixes for certificate checks, and with newer
  network and cryptography libraries: ten known vulnerabilities are gone from its code. Multiplexing
  (h2mux) now runs on Go's own HTTP/2. Go's two Android fixes, which the core needs on some older and
  32-bit phones, are now in the release build too.
- Fresh GeoIP and GeoSite databases.
- The zashboard web panel is updated to v3.29.1.
- Error messages in the log no longer quote the text that failed to parse: it could hold node
  addresses or keys.

## 0.4.36 — 2026-10-20

- My rules in the new look: the kinds of a rule as pills with icons, "Where" as a switch, the rules as rows
  on one card. Several apps can be picked at once — each gets its own rule — and a second tap unpicks one.
- Apps in the new look: the mode as a switch, the new search field, and "System" with "Select all" or
  "Clear all" on one line above the list. On a TV the search no longer opens the keyboard as soon as it
  gets the focus.
- The last old-style buttons and chips — node check, network stack, background work, logs and the cards
  on Home — now match the rest.

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
