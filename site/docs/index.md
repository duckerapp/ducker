---
title: Home
template: home.html
---

<section class="dk-hero" markdown>
<p class="dk-word" aria-hidden="true">DUCKER</p>
<div class="dk-slab" markdown>
<svg class="dk-duck" viewBox="0 0 24 26" aria-hidden="true" focusable="false"><path fill="currentColor" fill-rule="evenodd" d="M8.70 1.00 C9.90 1.55 11.00 1.75 12.70 1.72 C15.90 1.72 18.20 2.60 18.85 5.40 C18.98 6.00 19.05 6.70 19.10 7.20 C19.12 7.35 19.18 7.45 19.30 7.55 C20.00 8.20 20.70 8.50 21.60 8.65 C22.40 8.80 22.60 9.30 22.10 9.85 C21.30 10.60 20.30 11.00 19.10 11.05 C18.20 11.08 17.90 11.20 18.40 11.75 C20.20 13.45 20.95 14.55 21.25 15.95 C21.40 16.60 21.45 17.40 21.40 18.20 L21.20 26.00 L2.30 26.00 L2.20 18.00 C1.75 15.60 1.60 13.40 1.80 12.60 C1.95 12.05 2.30 11.95 3.00 11.95 C4.60 11.95 6.30 12.20 7.90 12.60 C8.30 12.70 8.55 12.60 8.35 12.35 C7.60 11.50 7.10 10.70 6.90 10.00 C6.20 7.70 7.20 5.10 9.35 3.62 C9.85 3.28 9.85 3.25 9.45 3.00 C8.60 2.45 8.10 1.30 8.70 1.00 Z M16.3 7.27 A0.72 0.72 0 1 0 14.86 7.27 A0.72 0.72 0 1 0 16.3 7.27 Z"/></svg>
<div class="dk-hero__inner" markdown>
<div markdown>

# Open-source VPN client for Android and Android TV

Powered by [mihomo](https://github.com/MetaCubeX/mihomo): one button, an accurate connection status, and no clutter on the screen.
{ .dk-lede }

[Install](users/install.md){ .dk-btn .dk-btn--solid } [Questions and answers](users/faq.md){ .dk-btn }
{ .dk-actions }

No ads, analytics, or reports to the developer. This site sends nothing either.
{ .dk-quiet }

</div>
<div class="dk-hero__shots" markdown>

![ducker on a TV: the Proxies screen](assets/shots/tv-proxies.png){ .dk-tv }
![ducker on a phone: Home with the power button](assets/shots/phone-home.png){ .dk-phone }

</div>
</div>
</div>
</section>

<section class="dk-section dk-on-paper" markdown>
<div class="dk-wrap dk-split" markdown>
<div markdown>

## One screen for one job

<div class="dk-claims" markdown>
<div markdown>

### One button

Home shows the power button, the current node with its delay, when the subscription ends and how much traffic is left. Everything else is a swipe away.

</div>
<div markdown>

### Honest status

Right after the tunnel comes up, ducker checks that traffic really goes through the node. If it doesn't, a card says so and offers the fastest node.

</div>
<div markdown>

### Errors in plain words

When the VPN stops or a subscription fails to update, you see why and what to do, not an error code.

</div>
</div>
</div>
<div class="dk-fan" markdown>

![Home](assets/shots/phone-home.png)
![Proxy groups](assets/shots/phone-groups.png)
![Profiles](assets/shots/phone-subscriptions.png)

</div>
</div>
</section>

<section class="dk-on-paper" markdown>
<div class="dk-wrap" markdown>

## Everything else, when you need it

<div class="dk-index" markdown>
<div markdown>

### Subscriptions your way

A link, a QR code or a file. Full mihomo configs work as they are; plain lists of links get a built-in profile.

</div>
<div markdown>

### Nodes

Groups with their icons, delay checks, favorites, search, and a node test with loss and delay.

</div>
<div markdown>

### Updates itself

A new version is one tap from Home, or let it install by itself at night.

</div>
<div markdown>

### Mobile network

A subscription and servers of its own for when the phone leaves Wi-Fi.

</div>
<div markdown>

### Connections

Which apps connect where, live: the app, the site, the rule, the node, the speed.

</div>
<div markdown>

### Wi-Fi sharing

The VPN's internet for a laptop or another phone, through a proxy on your network.

</div>
<div markdown>

### Apps and my rules

Send an app, a site or a whole country through the VPN, past it, or block it, without touching YAML.

</div>
<div markdown>

### Stays up

Autostart after a reboot, and step-by-step help with phones that like to kill VPN apps.

</div>
<div markdown>

### Privacy extras

Hide ducker from recent apps, disguise its icon, put the link and the config behind your fingerprint.

</div>
</div>
</div>
</section>

<section class="dk-slab dk-tv-slab" markdown>
<div class="dk-split" markdown>
<div markdown>

## On the TV, no typing with the remote

The same APK runs on Android TV and Google TV. The TV shows a QR code; scan it in ducker on your phone, and the subscription arrives over your home network, encrypted with a one-time key that exists only inside that code.

A dock on the left, a glowing focus frame, and every screen works with the D-pad.
{ .dk-quiet }

</div>
<div markdown>

![Android TV: adding a subscription by scanning a QR code with the phone](assets/shots/tv-add.png)

</div>
</div>
</section>

<section class="dk-section dk-on-paper" markdown>
<div class="dk-wrap dk-split" markdown>
<div markdown>

## For providers

ducker works with any regular mihomo subscription. It asks for one with this User-Agent, so panels that give mihomo clients a config give it to ducker too:

`User-Agent: mihomo/<core> ducker/<app> (android)`
{ .dk-ua }

Your name, logo, announcement and renewal link reach the app through response headers. No panel code changes.

<div class="dk-links" markdown>

- [Subscription for ducker](providers/start.md)
- [ducker-* headers](providers/headers.md)
- [Devices and limits](providers/devices.md)
- [Built-in template](providers/template.md)

</div>
</div>
<div markdown>

![Home with a provider's name, logo and announcement](assets/shots/brand-light.png){ .dk-brand .dk-only-light }
![Home with a provider's name, logo and announcement](assets/shots/brand-dark.png){ .dk-brand .dk-only-dark }

</div>
</div>
</section>

<section class="dk-on-paper" markdown>
<div class="dk-wrap" markdown>

Nothing is collected. The project has no server of its own.
{ .dk-statement }

<div class="dk-facts" markdown>
<div markdown>

**What goes out.** The subscription request to your provider, images it refers to, the check for a new ducker version, and your traffic through the provider's servers.

</div>
<div markdown>

**The core API.** Listens only on 127.0.0.1, behind a random secret created at install.

</div>
<div markdown>

**Open source.** GPL-3.0. Everything in the APK is built from the repository, and anyone can repeat the build.

</div>
</div>

The full list: [privacy policy](users/privacy.md) · [security](help/security.md)
{ .dk-more }

</div>
</section>

<section class="dk-slab dk-end" markdown>
<svg class="dk-duck" viewBox="0 0 24 26" aria-hidden="true" focusable="false"><path fill="currentColor" fill-rule="evenodd" d="M8.70 1.00 C9.90 1.55 11.00 1.75 12.70 1.72 C15.90 1.72 18.20 2.60 18.85 5.40 C18.98 6.00 19.05 6.70 19.10 7.20 C19.12 7.35 19.18 7.45 19.30 7.55 C20.00 8.20 20.70 8.50 21.60 8.65 C22.40 8.80 22.60 9.30 22.10 9.85 C21.30 10.60 20.30 11.00 19.10 11.05 C18.20 11.08 17.90 11.20 18.40 11.75 C20.20 13.45 20.95 14.55 21.25 15.95 C21.40 16.60 21.45 17.40 21.40 18.20 L21.20 26.00 L2.30 26.00 L2.20 18.00 C1.75 15.60 1.60 13.40 1.80 12.60 C1.95 12.05 2.30 11.95 3.00 11.95 C4.60 11.95 6.30 12.20 7.90 12.60 C8.30 12.70 8.55 12.60 8.35 12.35 C7.60 11.50 7.10 10.70 6.90 10.00 C6.20 7.70 7.20 5.10 9.35 3.62 C9.85 3.28 9.85 3.25 9.45 3.00 C8.60 2.45 8.10 1.30 8.70 1.00 Z M16.3 7.27 A0.72 0.72 0 1 0 14.86 7.27 A0.72 0.72 0 1 0 16.3 7.27 Z"/></svg>
<div class="dk-end__inner" markdown>

## Install ducker

Android 10 or newer · phones and TVs · free
{ .dk-quiet }

[Install](users/install.md){ .dk-btn .dk-btn--solid } [Telegram channel](https://t.me/ducker_mihomo){ .dk-btn } [GitHub](https://github.com/duckerapp/ducker){ .dk-btn }
{ .dk-actions }

</div>
</section>
