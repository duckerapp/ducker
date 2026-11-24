# Devices and limits

## What the app sends

Every HTTPS subscription request from ducker includes four headers:

| Header | Value |
|---|---|
| `x-hwid` | device id |
| `x-device-os` | `Android` |
| `x-ver-os` | Android version |
| `x-device-model` | manufacturer and model |

These headers are not sent to an `http://` URL, which is visible to the network along the
route. If your server redirects the request, device data follows while the entire chain
has remained HTTPS.

## Where the id comes from

It is `ANDROID_ID`, which Android assigns separately to each app. This has two consequences:

- The same phone has a different id in another client. A user switching to ducker occupies
  a new device slot in the panel; with a one-device limit, the old device must be unlinked.
- Uninstalling and reinstalling ducker usually leaves the id unchanged.

Users can see their id under **Settings → Privacy → Device HWID** ("Device ID" on TV). It is hidden
until tapped and can be read out to support.

## The switch

The same section has a switch for sending the id. It disables all four headers at once.
ducker explicitly warns that a provider which counts devices may stop returning the
subscription if no id is supplied.

## When the limit is reached

The panel signals rejection with a response header:

| Response header | What the user sees |
|---|---|
| `x-hwid-max-devices-reached: true` | “Device limit reached” |
| `x-hwid-not-supported: true` | “The provider rejected this device ID” |

Remnawave adds these when device tracking is enabled. PasarGuard refuses with HTTP 403 and
`Device limit reached` or `HWID header required` in its JSON answer; ducker reads them as the
first and the second header. In either case, ducker:

- does not overwrite the previous profile with the returned placeholder;
- prevents connecting while the rejection remains in effect, so the limit is enforced;
- shows a Home card with your `announce` text and a **Support** button from `support-url`;
- keeps the **Update** button available: after unlinking a device in the panel, the user
  taps it to retry.
