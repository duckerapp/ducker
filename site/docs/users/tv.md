# Installing on a TV

**ducker already installed on the TV?** Go straight to [sending the subscription from an
iPhone or another browser](#browser-transfer). ducker runs on Android TV/Google TV; the
iPhone only sends the link and does not need a ducker app.

The TV needs the ducker file, an APK. The easiest way to get it there is from your phone over the home network,
with [LocalSend](https://localsend.org): free, open source and without ads. It goes only on the TV; the phone
sends the file from its ordinary browser.

You need a TV or a box with Android TV or Google TV and a phone, both on the same home Wi-Fi.

## 1. Download ducker on the phone

On the phone, open [Releases](https://github.com/duckerapp/ducker/releases) and start with
[`ducker-armeabi-v7a.apk`](https://github.com/duckerapp/ducker/releases/latest/download/ducker-armeabi-v7a.apk).
If it doesn't install on the TV at step 5, try
[`ducker-arm64-v8a.apk`](https://github.com/duckerapp/ducker/releases/latest/download/ducker-arm64-v8a.apk).
ARM TVs and boxes that support only 64-bit apps need this version. Do not install the APK on the phone itself.

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

<a id="browser-transfer"></a>

### From an iPhone or another device without ducker

1. Connect the TV and sending device to the same home Wi-Fi. Keep ducker's **Add subscription**
   screen with its QR code open on the TV.
2. On an iPhone or Android phone, scan that QR with the ordinary camera and open its link.
   A computer can also open the complete link read from the QR.
3. The **Subscription to TV** page opens in the browser. Paste the subscription link from
   your provider into **Subscription link** and press **Send**.
4. Wait for the confirmation. The TV receives the link and downloads the subscription itself.
   Press the power button on its Home screen and allow the VPN if Android asks.

No app is needed on the sender. This QR is for sending a subscription to ducker, not for
installing the APK; LocalSend's earlier QR transfers the APK file instead. If the QR has
expired or the receiving screen was closed, reopen it and scan the new code. If the page
will not open, check both devices are on the same network; guest Wi-Fi may isolate them.

The page encrypts the link before sending it to the TV. The page itself loads over HTTP.
Open it only on your own home Wi-Fi. Someone controlling the network could replace the page.

### From ducker on an Android phone

Open **Profiles → Add → Scan QR**, scan the TV's code and confirm which subscription to
send. ducker encrypts it in the app; this route does not load the browser page.

### With the remote

Select the link field on the TV, type the provider's link and press **Add**.

![ducker on a TV: Add subscription with the QR code and the link field](../assets/shots/tv-guide-add.png){ loading=lazy }

![Typing the link with the on-screen keyboard and the remote](../assets/shots/tv-guide-typed.png){ loading=lazy }

Done: press the power button on Home.

![ducker's Home on a TV after the subscription is added](../assets/shots/tv-guide-home.png){ loading=lazy }

After that, LocalSend is no longer needed: new versions of ducker come to the app itself. Uncheck
**Automatically accept requests** or uninstall LocalSend.

## No phone at hand

Copy the APK to a USB stick from a computer, plug it into the TV and open the file with a file manager; many TVs
have one built in. Then continue as in step 5: allow installs from that file manager and press **Install**.

If your TV requests permission for apps from unverified developers, see
[what to tap](faq.md#android-asks-to-allow-apps-from-unverified-developers).
