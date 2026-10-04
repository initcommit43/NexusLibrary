---
title: Privacy Policy
updated: 4 October 2026
---

NexusLibrary is a media tracker. It keeps the list of things you are reading, watching and
playing, and it needs an account to know whose list is whose. This page says exactly what that
means for your data.

## Who is responsible

The controller for the purposes of the GDPR is Maximilian Müller, 4030 Linz, Austria. Questions
about your data, or any of the rights below, go to contact@nexuslibrary.net.

## What is stored, and why

### Your account

Your email address, your username, and your password — the password only ever as a bcrypt hash,
which cannot be read back. You sign in with either the username or the email address, and the
email address is how a password reset link would reach you. Legal basis: performance of the
contract you enter into by creating an account, Art. 6(1)(b) GDPR.

Your date of birth, given when you register. An account made before registration asked for it can
add it once in Settings. It is stored as the date rather than as an age, and it cannot be changed
afterwards from the app; to correct a mistake, write to us. It is used for two things only:
refusing accounts under 16, and deciding whether the 18+ content setting is available to you. It
is never shown on your profile and no proof of it is asked for. It is included in your data
export and deleted with your account. Legal basis: our legitimate interest in keeping minors out of
the service and away from adult content, Art. 6(1)(f) GDPR.

Your content settings: whether 18+ titles are shown to you and whether their covers are blurred.
Legal basis: Art. 6(1)(b) GDPR.

### Your library

What you track and everything you record about it: status, rating, progress, start and finish
dates, private notes, favourites, reviews you write, and a log of the changes you make so the
activity feed and the statistics pages have something to show. This is the service itself. Legal
basis: Art. 6(1)(b) GDPR.

### Connected accounts

If you connect Steam, AniList, MyAnimeList or Simkl, we store your identifier on that service and
the access token it issued, so your library can be imported and kept in step. Tokens are
encrypted at rest with AES-GCM and are never included in a data export. Connecting is entirely
your choice and nothing else in the app depends on it. Legal basis: your consent,
Art. 6(1)(a) GDPR, withdrawn at any time by disconnecting the account in Settings, which deletes
the stored tokens.

### Sessions

When you sign in, a record of that session is stored so it can be ended again — its identifier,
which kind of client it belongs to, when it was issued and when it expires. Sessions expire after
30 days and are deleted after that. Signing out, signing out everywhere, changing your password
or resetting it all revoke them immediately. Legal basis: Art. 6(1)(b) GDPR.

### Confirmation and reset links

When you create an account, and whenever you ask for a new password, a single-use link is sent to
your email address. What is stored is not the link but a SHA-256 digest of it, together with the
account it belongs to and the times it was issued, expires and was used — so a link can be
checked and spent without this database ever holding one that still works. A confirmation link
lasts 48 hours and a reset link 30 minutes; expired rows are deleted daily.

How many links an account has been sent in the past hour is counted from those same rows, and
capped, so that no address can be buried in mail by someone typing it into the form over and
over. Legal basis: performance of the contract, Art. 6(1)(b) GDPR, for sending the link at all;
our legitimate interest in not being used to send unwanted mail, Art. 6(1)(f) GDPR, for the
count.

### Your IP address

Your IP address is used to count requests to the sign-in, registration and password-reset
endpoints, so that nobody can guess passwords at speed. The count is held in memory for one
minute at a time and then discarded. It is never written to the database and never associated
with your account. Legal basis: our legitimate interest in keeping accounts from being broken
into, Art. 6(1)(f) GDPR.

### The bot check

The website's sign-up and password-reset forms run Cloudflare Turnstile, which is what keeps them
from being filled in automatically. It loads a script from Cloudflare and sends Cloudflare your
IP address together with the token the challenge produces, so that Cloudflare can confirm a
browser really solved it. Cloudflare states that Turnstile is not used to track visitors across
sites and that its data is not used for advertising. On our side nothing is kept but the answer:
passed, or not. It runs on those two forms and on no other page of the website. Legal basis: our
legitimate interest in keeping automated sign-ups and reset requests out, Art. 6(1)(f) GDPR.

The mobile app does not run it, and discloses nothing to Cloudflare at any point: Turnstile is a
browser check, and the app is not a browser. Two things described above
stand in its place — an account created from the app must confirm its email address before it
can be used, and requests for a reset link are counted per account. Neither sends anything to
anyone outside this service. Legal basis for both: Art. 6(1)(f) GDPR.

### Server logs

The server records errors and warnings so faults can be diagnosed. Reset links, passwords, tokens
and encryption keys are deliberately never logged. Legal basis: Art. 6(1)(f) GDPR.

## What is not collected

There is no analytics, no tracking, no advertising, no profiling and no automated decision-making
within the meaning of Art. 22 GDPR. The only third-party script on the website is the Cloudflare
Turnstile bot check described above, which loads on its sign-up and password-reset forms and
nowhere else; the mobile app loads no third-party code at all. Your data is not sold, rented or
shared for anyone else's purposes.

## Cookies and local storage

Up to two cookies, and four values kept in your browser's local storage. All of them are needed
for the site to work as you asked it to, so none of them requires consent under § 165(3) TKG 2021:

- `nexus_refresh` — the cookie that keeps you signed in. It is httpOnly, so no script can read
  it, restricted to this site, and scoped to the sign-in endpoints alone. It lasts 30 days, or
  until you sign out.
- `nexus_site` — set only while the site is private, once you enter its password, so you are not
  asked again. It is httpOnly, holds an expiry and a signature rather than the password, and
  lasts 30 days.
- `nexus-theme` — whether you chose light, dark or your system setting.
- `nexus-module` — which module you were last in, so a reload lands where you left.
- `nexus-library-view` — whether your library shows as a grid or a table.
- `nexus.home.folded.*` — which sections of the home page you collapsed, one entry per module.

The last four never leave your browser. Clearing your site data removes all of them; you will be
signed out and the display preferences will return to their defaults. The
[Cookie Policy](/cookies) says the same thing at more length, including what each cookie's flags
mean.

None of this applies to the mobile app, which sets no cookies at all: it keeps its sign-in token
in the platform keychain your device provides, and its display preferences on the device.

## Who else sees it

The application and its database run on Railway, which processes data on our instructions as a
processor under Art. 28 GDPR. Railway is based in the United States, so hosting involves a
transfer outside the EEA, made on the safeguards in Art. 46 GDPR.

Opening the website's sign-up or password-reset form loads the bot check described above, which
discloses your IP address to Cloudflare. Cloudflare processes it on our instructions as a
processor under Art. 28 GDPR, and is likewise based in the United States, so this too is a
transfer outside the EEA made on the safeguards in Art. 46 GDPR. No other page of the website
contacts Cloudflare, and the mobile app never does — so if you only ever use the app, no transfer
to Cloudflare happens on your account at all.

When you search or open a title, a request goes to the relevant metadata provider — IGDB, TMDB,
AniList, Simkl or Open Library. These requests are made by our server, not by your browser, so
your IP address is not disclosed to them. When you connect an account, we exchange data with that
service on your behalf, under their privacy policy as well as this one. They are listed on the
[Credits](/credits) page.

## How long it is kept

Your account and everything attached to it are kept until you delete the account. Deleting it
removes your entries, reviews, activity, notifications, preferences, connected accounts and
stored tokens, immediately and permanently. Sessions expire after 30 days; confirmation links
after 48 hours and password reset links after 30 minutes, and rows for expired links are swept
daily.

## Your rights

You have the right of access (Art. 15), rectification (Art. 16), erasure (Art. 17), restriction
of processing (Art. 18), data portability (Art. 20) and objection (Art. 21). Where processing
rests on consent, you may withdraw it at any time under Art. 7(3), without affecting what was
lawful before.

Two of these you can exercise yourself, without asking anyone: Settings offers a full export of
your data as a file, and account deletion. Everything else goes to contact@nexuslibrary.net.

You may also complain to a supervisory authority under Art. 77 GDPR — for us,
the Austrian Data Protection Authority (Datenschutzbehörde),
Barichgasse 40–42, 1030 Vienna, [dsb.gv.at](https://www.dsb.gv.at).

## Age

This service is not intended for anyone under 16, and registration refuses a date of birth below
that. If you believe an account here belongs to someone under 16, write to contact@nexuslibrary.net and we
will delete it.

## Changes

If this policy changes in a way that affects you, you will be asked to read and accept the new
version the next time you sign in. The date at the top says when it last changed.
