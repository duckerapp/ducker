---
title: Home
template: home.html
hide:
  - navigation
  - toc
---

<section class="dk-hero" markdown>
<p class="dk-word" aria-hidden="true">DUCKER</p>
<div class="dk-slab" markdown>
<svg class="dk-duck" viewBox="27.2 27.4 52.6 42.1" aria-hidden="true" focusable="false"><path fill="currentColor" d="M44.83,28.06 C43.56,28.68 44.79,31.55 46.93,32.90 C47.85,33.50 47.85,33.52 46.64,34.34 C41.29,37.98 38.81,44.40 40.61,50.02 C41.13,51.69 42.57,54.04 44.21,55.91 C44.68,56.44 44.69,56.47 44.51,56.65 C44.33,56.83 44.25,56.82 42.60,56.38 C36.97,54.85 31.09,54.24 29.28,55.00 C28.70,55.24 28.23,55.74 28.02,56.35 C27.73,57.16 27.76,60.53 28.08,62.23 C29.43,69.76 33.84,76.03 39.98,79.17 C41.28,79.85 41.50,79.92 43.37,80.49 C48.58,82.07 55.84,82.38 61.81,81.27 C69.98,79.74 75.15,75.44 76.03,69.46 C76.22,68.15 76.09,65.89 75.77,64.55 C74.92,61.12 73.32,58.60 69.47,54.64 C67.40,52.52 67.38,52.62 69.93,52.59 C71.93,52.56 72.80,52.41 74.34,51.88 C77.41,50.79 79.34,48.51 78.21,47.30 C77.89,46.97 77.63,46.86 76.55,46.68 C74.33,46.33 72.80,45.64 71.35,44.36 L70.45,43.57 L70.45,43.01 C70.45,42.15 70.17,40.17 69.88,39.05 C68.52,33.67 64.50,30.34 58.78,29.84 C58.06,29.77 56.20,29.73 54.63,29.74 C50.95,29.75 49.08,29.47 46.88,28.55 C45.29,27.89 45.22,27.87 44.83,28.06 Z M62.99,41.86 C63.85,42.29 64.19,43.51 63.69,44.38 C63.09,45.44 61.54,45.58 60.75,44.66 C60.11,43.89 60.12,43.01 60.80,42.28 C61.39,41.63 62.22,41.48 62.99,41.86 Z"/></svg>
<div class="dk-hero__inner" markdown>
<div markdown>

# Open-source VPN client for Android and Android TV

Powered by [mihomo](https://github.com/MetaCubeX/mihomo): one button, an accurate connection status, and no clutter on the screen.
{ .dk-lede }

[Install](users/install.md){ .dk-btn .dk-btn--solid } [Questions and answers](users/faq.md){ .dk-btn }
{ .dk-actions }

No ads, analytics, or reports to the developer. No analytics or tracking scripts on this site; your browser requests its pages from GitHub Pages.
{ .dk-quiet }

</div>
<div class="dk-hero__shots" markdown>

![ducker on a TV: the Proxies screen](assets/shots/tv-proxies.png){ .dk-tv }
![ducker on a phone: Home with the power button](assets/shots/phone-home.png){ .dk-phone }

</div>
</div>
</div>
</section>

<section class="dk-steps-section dk-on-paper" markdown>
<div class="dk-wrap" markdown>

## Connected in three steps

<div class="dk-steps" markdown>
<div markdown>

<span class="dk-step__n" aria-hidden="true">1</span>

### Download the APK

From [Releases](https://github.com/duckerapp/ducker/releases) on GitHub. `arm64-v8a` fits almost any phone made after 2017; [which file to take](users/install.md).

</div>
<div markdown>

<span class="dk-step__n" aria-hidden="true">2</span>

### Add the subscription

**Profiles → Add**: paste the link from your provider, scan its QR code or pick a file.

</div>
<div markdown>

<span class="dk-step__n" aria-hidden="true">3</span>

### Press the button

The power button on Home. Android asks once to allow the VPN, and you are connected.

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

Right after the tunnel comes up, ducker checks whether the selected node answers a connection probe. If it doesn't, a card says so and offers the fastest node.

</div>
<div markdown>

### Errors in plain words

When the VPN stops or a subscription fails to update, you see why and what to do, not an error code.

</div>
</div>
</div>
<div class="dk-fan" markdown>

![Home](assets/shots/phone-home.png){ loading=lazy }
![Proxy groups](assets/shots/phone-groups.png){ loading=lazy }
![Profiles](assets/shots/phone-subscriptions.png){ loading=lazy }

</div>
</div>
</section>

<section class="dk-slab dk-tv-slab" markdown>
<div class="dk-split" markdown>
<div markdown>

## DUCKER Glass

A second style for phones on Android 13 and newer: cards, the dock and sheets are liquid glass over a picture, Liquid metal or Fern. In beta; switch it in Settings → Appearance → Style.

Air stays as it was, light or dark.
{ .dk-quiet }

</div>
<div markdown>

![DUCKER Glass: Home, an open group's nodes and the All nodes sheet over Liquid metal, and Home over Fern](assets/shots/glass.png){ .dk-poster loading=lazy }

</div>
</div>
</section>

<section class="dk-section dk-on-paper" markdown>
<div class="dk-wrap" markdown>

## Everything else, when you need it


Read [questions and answers](users/faq.md) for subscriptions, configuration templates,
app rules, Wi-Fi sharing and updates. [All features](https://github.com/duckerapp/ducker#features)
are listed in the README.

</div>
</section>

<section class="dk-slab dk-tv-slab" markdown>
<div class="dk-split" markdown>
<div markdown>

## On the TV, no typing with the remote

The same APK runs on Android TV and Google TV. Scan the TV's QR with an iPhone's or Android phone's camera, open the browser page, paste the subscription link and press Send. No app is needed on the phone; use your own home Wi-Fi. ducker's Android scanner can also send an existing subscription.

[How to install ducker on a TV, step by step](users/tv.md)

A dock on the left, a glowing focus frame, and every screen works with the D-pad.
{ .dk-quiet }

</div>
<div markdown>

![Android TV: adding a subscription by scanning a QR code with the phone](assets/shots/tv-add.png){ loading=lazy }

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

![Home with a provider's name, logo and announcement](assets/shots/brand-light.png){ .dk-brand .dk-only-light loading=lazy }
![Home with a provider's name, logo and announcement](assets/shots/brand-dark.png){ .dk-brand .dk-only-dark loading=lazy }

</div>
</div>
</section>

<section class="dk-on-paper" markdown>
<div class="dk-wrap" markdown>

No analytics or reports to the developer.
{ .dk-statement }

<div class="dk-facts" markdown>
<div markdown>

**What goes out.** The subscription request to your provider, images it refers to, the check for a new ducker version, and your traffic through the provider's servers.

</div>
<div markdown>

**The core API.** Listens only on 127.0.0.1, behind a random secret created at install.

</div>
<div markdown>

**Open source.** GPL-3.0. The app is built from the repository, and anyone can repeat the build.

</div>
</div>

The full list: [privacy policy](users/privacy.md) · [security](help/security.md)
{ .dk-more }

</div>
</section>

<section class="dk-slab dk-end" markdown>
<svg class="dk-duck" viewBox="27.2 27.4 52.6 42.1" aria-hidden="true" focusable="false"><path fill="currentColor" d="M44.83,28.06 C43.56,28.68 44.79,31.55 46.93,32.90 C47.85,33.50 47.85,33.52 46.64,34.34 C41.29,37.98 38.81,44.40 40.61,50.02 C41.13,51.69 42.57,54.04 44.21,55.91 C44.68,56.44 44.69,56.47 44.51,56.65 C44.33,56.83 44.25,56.82 42.60,56.38 C36.97,54.85 31.09,54.24 29.28,55.00 C28.70,55.24 28.23,55.74 28.02,56.35 C27.73,57.16 27.76,60.53 28.08,62.23 C29.43,69.76 33.84,76.03 39.98,79.17 C41.28,79.85 41.50,79.92 43.37,80.49 C48.58,82.07 55.84,82.38 61.81,81.27 C69.98,79.74 75.15,75.44 76.03,69.46 C76.22,68.15 76.09,65.89 75.77,64.55 C74.92,61.12 73.32,58.60 69.47,54.64 C67.40,52.52 67.38,52.62 69.93,52.59 C71.93,52.56 72.80,52.41 74.34,51.88 C77.41,50.79 79.34,48.51 78.21,47.30 C77.89,46.97 77.63,46.86 76.55,46.68 C74.33,46.33 72.80,45.64 71.35,44.36 L70.45,43.57 L70.45,43.01 C70.45,42.15 70.17,40.17 69.88,39.05 C68.52,33.67 64.50,30.34 58.78,29.84 C58.06,29.77 56.20,29.73 54.63,29.74 C50.95,29.75 49.08,29.47 46.88,28.55 C45.29,27.89 45.22,27.87 44.83,28.06 Z M62.99,41.86 C63.85,42.29 64.19,43.51 63.69,44.38 C63.09,45.44 61.54,45.58 60.75,44.66 C60.11,43.89 60.12,43.01 60.80,42.28 C61.39,41.63 62.22,41.48 62.99,41.86 Z"/></svg>
<div class="dk-end__inner" markdown>

## Install ducker

Android 8 or newer · phones and TVs · free
{ .dk-quiet }

[Install](users/install.md){ .dk-btn .dk-btn--solid } [Telegram channel](https://t.me/ducker_news){ .dk-btn } [GitHub](https://github.com/duckerapp/ducker){ .dk-btn }
{ .dk-actions }

</div>
</section>
