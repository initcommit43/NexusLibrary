---
title: Cookie Policy
updated: 19 September 2026
---

This page lists everything NexusLibrary keeps in your browser, what each item is for and how long
it stays. There is nothing here for advertising, analytics, profiling or tracking between sites,
because the site does none of those things.

## Why you are not asked to consent

Every item below is needed to provide the service you asked for. Storage of that kind does not
require consent under § 25(2) no. 2 TDDDG, which is why this site shows no cookie banner. Nothing
is set for any other purpose, so there is nothing to opt out of — and a banner asking permission
for things that cannot be refused without breaking the site would be theatre.

## Cookies

Two, at most, and only one of them on a public deployment.

- `nexus_refresh` — keeps you signed in. Set when you sign in, cleared when you sign out, and
  good for 30 days. It is `httpOnly`, so no script on the page can read it; `SameSite=Strict`, so
  it is never sent from another site; and scoped to the sign-in endpoints alone rather than to
  the whole site.
- `nexus_site` — set only while the site is closed to the public, after you enter the site
  password, so you are not asked for it on every page. It holds an expiry and a signature, never
  the password itself, and lasts 30 days.

Both are first-party: they are set by this site, read by this site, and sent nowhere else.
Neither identifies you to anybody outside it.

## Local storage

Four values, kept by your browser and never sent to the server. They hold display preferences,
not identity, and there is nothing in them worth anyone reading.

- `nexus-theme` — whether you chose light, dark, or your system setting.
- `nexus-module` — which module you were last in, so a reload lands where you left off.
- `nexus-library-view` — whether your library shows as a grid or a table.
- `nexus.home.folded.*` — which sections of the home page you collapsed, one entry per module.

## The bot check

The sign-up and password-reset forms, and no other page, load Cloudflare Turnstile to tell a
person from a script. It runs in a frame served by Cloudflare, and anything it stores is set by
Cloudflare on its own domain under its own policy — this site sets nothing on its behalf and
receives nothing from it but a pass or a fail. What that discloses to Cloudflare, and on what
basis, is set out in the [Privacy Policy](/privacy).

## The app

The NexusLibrary mobile app sets no cookies. It keeps its sign-in token in the platform keychain
instead, which is why it is never shown this page.

## Removing them

Clearing this site's data in your browser removes all of it. You will be signed out, your display
preferences will return to their defaults, and if the site is still closed to the public you will
be asked for its password again. Blocking cookies entirely will stop you staying signed in;
nothing else depends on them.

## Changes

If what the site stores changes, this page changes with it and you will be asked to acknowledge
the new version when you next sign in. The date at the top says when it last changed.

Questions about any of this go to [CONTACT EMAIL]. How your data is handled more generally is set
out in the [Privacy Policy](/privacy).
