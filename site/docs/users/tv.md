# Installing on a TV

The TV needs the ducker file, an APK. The easiest way to get it there is from your phone over the home network,
with [LocalSend](https://localsend.org): free, open source and without ads. It goes only on the TV; the phone
sends the file from its ordinary browser.

You need a TV or a box with Android TV or Google TV and a phone, both on the same home Wi-Fi.

## 1. Download ducker on the phone

On the phone, open [Releases](https://github.com/duckerapp/ducker/releases) and download
`ducker-…-armeabi-v7a.apk`: it installs on almost any TV. A current TV or box needs
`ducker-…-arm64-v8a.apk`. If the file does not install at step 5, repeat with the other one. Do not install the
file on the phone itself.

## 2. Install LocalSend on the TV

On the TV, open Google Play, search for LocalSend and install it.

## 3. Turn on receiving by link

Open LocalSend on the TV and choose **Receive via link**: right after it starts, that is down twice and OK.
Check **Automatically accept requests**, so the TV does not ask again. Then press the QR code icon next to the
address.

LocalSend's selection frame is faint: look for the item that is slightly lighter than the rest.

![LocalSend on a TV: Receive via link with "Automatically accept requests" checked](../assets/shots/tv-guide-link.png){ loading=lazy }

![The link's QR code on the TV screen](../assets/shots/tv-guide-qr.png){ loading=lazy }

## 4. Send the file from the phone

Point the phone's camera at the QR code and open the link. In the browser, tap **Upload** and pick the ducker
file you downloaded. When the TV shows **Finished**, the file is there.

If the QR code does not scan, type the address under it into the phone's browser, such as
`http://192.168.1.23:53317`; yours will be different.

![The TV has received the ducker file: Receiving files, Finished](../assets/shots/tv-guide-received.png){ loading=lazy }

## 5. Install ducker

On the TV, press up twice to select the file's row, then OK. Android asks to allow installs from LocalSend: turn
the switch on and press Back.

![Android TV: Install unknown apps, the switch for LocalSend](../assets/shots/tv-guide-unknown.png){ loading=lazy }

**Do you want to install this app?** appears. The focus is on **Cancel**: press left, to **Install**, then OK.
Then **Open**.

![Do you want to install this app? with Install selected](../assets/shots/tv-guide-install.png){ loading=lazy }

## 6. Add the subscription

In ducker on the TV, press **Add subscription**. There are two ways:

- **A QR code from the phone,** if ducker is on the phone already: there, **Profiles → Add → Scan QR**, and point
  the camera at the code on the TV. The subscription goes to the TV over the home network, encrypted.
- **The link by hand:** select the field, type your provider's link with the on-screen keyboard and press **Add**.

![ducker on a TV: Add subscription with the QR code and the link field](../assets/shots/tv-guide-add.png){ loading=lazy }

![Typing the link with the on-screen keyboard and the remote](../assets/shots/tv-guide-typed.png){ loading=lazy }

Done: press the power button on Home.

![ducker's Home on a TV after the subscription is added](../assets/shots/tv-guide-home.png){ loading=lazy }

After that, LocalSend is no longer needed: new versions of ducker come to the app itself. Uncheck
**Automatically accept requests** or uninstall LocalSend.

## No phone at hand

Copy the APK to a USB stick from a computer, plug it into the TV and open the file with a file manager; many TVs
have one built in. Then continue as in step 5: allow installs from that file manager and press **Install**.

From 2027, Google's TVs ask one more permission, for apps from unverified developers:
[what to tap](faq.md#android-asks-to-allow-apps-from-unverified-developers).
