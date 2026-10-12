# Installation

Download an APK from [releases](https://github.com/duckerapp/ducker/releases) and open it on
your phone or TV. Android will ask you to allow installation from this source; this is
normal for apps outside Play. Google Play Protect may also offer to scan the app first:
[what to tap](faq.md#google-play-protect-asks-to-scan-the-app).

Which file to choose:

- `arm64-v8a`: almost any phone newer than 2017;
- `armeabi-v7a`: older phones and many TVs; use it if the first APK will not install.

Android 10 or later is required. Every release includes `SHA256SUMS` with the APK checksums.

## Your first subscription

Open **Subscriptions → +** and paste the provider's URL, or open a
`ducker://install-config?url=…` link directly from an email or chat.

All methods — URL, QR code, and file — are described under
[How to add a subscription](faq.md#how-to-add-a-subscription).

## On a TV

The same APK works on Android TV and Google TV. You do not need to type the URL with a remote:
press **+** on the TV to show a QR code. Scan it in ducker on your phone
(**Subscriptions → + → Scan QR**) and confirm sending. The subscription is sent to the TV
encrypted over the home network. The phone and TV must be on the same home network.

## External control

The app accepts intents, which are useful for Tasker and shortcuts.

For external START/STOP/TOGGLE, enable **Settings → Control from other apps**.
Access is disabled by default, including on existing installations. Permission applies to
all apps able to send these commands. Built-in widgets, the tile, and shortcuts work
independently of this permission; importing through a URL requires confirmation.

| Action | Intent |
|---|---|
| start | `am start -a app.ducker.android.action.START_CLASH` |
| stop | `am start -a app.ducker.android.action.STOP_CLASH` |
| toggle | `am start -a app.ducker.android.action.TOGGLE_CLASH` |
| add a subscription | `ducker://install-config?url=<subscription-url>` |
