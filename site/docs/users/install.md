# Installation

Download an APK from [releases](https://github.com/duckerapp/ducker/releases) and open it on
your phone or TV. Android will ask you to allow installation from this source; this is
normal for apps outside Play. Google Play Protect may also offer to scan the app first:
[what to tap](faq.md#google-play-protect-asks-to-scan-the-app).

Which file to choose:

- `arm64-v8a`: almost any phone newer than 2017;
- `armeabi-v7a`: older phones and many TVs; use it if the first APK will not install.

Android 10 or later is required. You install ducker by hand once; after that it
[updates itself](#updates).

## Your first subscription

Open **Profiles → Add** and paste the provider's link, scan its QR code, or pick a file. A
`ducker://install-config?url=…` link opens the same window directly from an email or chat.
Then press the power button on **Home** and allow Android's VPN request.

All methods are described under [How to add a subscription](faq.md#how-to-add-a-subscription).

## On a TV

The same APK works on Android TV and Google TV. You do not need to type the link with a remote:
press **+** on the TV to show a QR code. Scan it in ducker on your phone
(**Profiles → Add → Scan QR**) and confirm sending. The subscription is sent to the TV
encrypted over the home network. The phone and TV must be on the same home network.

How to get the APK onto the TV in the first place, step by step: [Installing on a TV](tv.md).

## Updates

- **ducker checks by itself** twice a day and when you open it. A new version shows up on Home
  and in a notification, with what's new; one tap downloads and installs it, and the VPN comes
  back by itself.
- **Update automatically** (Settings): new versions download over Wi-Fi and install while the
  screen is off, usually at night. Off until you say yes. Android 12 and newer; on Android 10–11
  Android asks for a confirmation each time.
- **Beta versions** (Settings): new versions a few days before everyone else. A beta that goes
  well is then given to everyone as the very same file.
- **Where from.** The list of versions comes from GitHub, or from the
  [GitLab mirror](https://gitlab.com/duckerx/ducker) or jsDelivr when GitHub does not answer.
  Before installing, ducker checks the file's checksum and that it is signed with the same key as
  the installed app: a file from anyone else is not installed.

## Checking the file

- Each release comes with `SHA256SUMS`, the checksums of its APK files.
- Each release's notes link a [VirusTotal](https://www.virustotal.com) report for every APK:
  dozens of antivirus engines have looked at that exact file. You can upload your copy there
  yourself and compare.
- The app is built from the [repository](https://github.com/duckerapp/ducker#build); the build also downloads
  the zashboard panel's release (checked by its SHA-256) and the country and site databases of
  MetaCubeX/meta-rules-dat. You can repeat the build yourself.

## Google's developer verification

From 2027, certified Android phones and TVs install apps from outside Google Play only if their
developer is registered with Google; in Brazil, Indonesia, Singapore and Thailand this started in
2026. ducker is not registered. On such devices Android will ask once to allow apps from
unverified developers: the setting needs your PIN, a restart and a 24-hour wait. After that
ducker installs and updates as before; turning the setting off again stops its updates.
Installing over `adb` is not affected.
[What to tap](faq.md#android-asks-to-allow-apps-from-unverified-developers).

## External control

The app accepts intents, which are useful for Tasker and shortcuts.

For external START/STOP/TOGGLE, enable **Settings → Privacy → Control from other apps**.
Access is disabled by default, including on existing installations. Permission applies to
all apps able to send these commands. Built-in widgets, the tile, and shortcuts work
independently of this permission; importing through a URL requires confirmation.

| Action | Intent |
|---|---|
| start | `am start -a app.ducker.android.action.START_CLASH` |
| stop | `am start -a app.ducker.android.action.STOP_CLASH` |
| toggle | `am start -a app.ducker.android.action.TOGGLE_CLASH` |
| add a subscription | `ducker://install-config?url=<subscription-url>` |
