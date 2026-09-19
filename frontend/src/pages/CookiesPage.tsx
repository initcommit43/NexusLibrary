import { Link } from 'react-router-dom'
import { LegalLayout } from '../components/LegalLayout'

/*
 * TODO before this goes public: [BRACKETED] values are placeholders, as on the other legal
 * pages. The list below is the whole truth about what this site stores and has to be kept
 * that way — if a key is added in code and not here, this page is wrong.
 */

/**
 * What the site stores in your browser.
 *
 * <p>Web only. A native client sets no cookies at all and is never offered this document —
 * see {@code Agreement} on the backend, which is what decides that.
 *
 * <p>There is no consent banner and this page explains why rather than apologising for it:
 * everything listed is needed to do what the reader asked for, which is the § 25(2) TDDDG
 * exception. Accepting this document is confirming you were told, not granting a permission
 * — nothing here waits on an answer.
 */
export const CookiesPage = () => (
  <LegalLayout title="Cookie Policy" updated="19 September 2026">
    <p>
      This page lists everything NexusLibrary keeps in your browser, what each item is for and
      how long it stays. There is nothing here for advertising, analytics, profiling or
      tracking between sites, because the site does none of those things.
    </p>

    <h2>Why you are not asked to consent</h2>
    <p>
      Every item below is needed to provide the service you asked for. Storage of that kind
      does not require consent under § 25(2) no. 2 TDDDG, which is why this site shows no
      cookie banner. Nothing is set for any other purpose, so there is nothing to opt out of
      — and a banner asking permission for things that cannot be refused without breaking
      the site would be theatre.
    </p>

    <h2>Cookies</h2>
    <p>Two, at most, and only one of them on a public deployment.</p>
    <ul>
      <li>
        <code>nexus_refresh</code> — keeps you signed in. Set when you sign in, cleared when
        you sign out, and good for 30 days. It is <code>httpOnly</code>, so no script on the
        page can read it; <code>SameSite=Strict</code>, so it is never sent from another
        site; and scoped to the sign-in endpoints alone rather than to the whole site.
      </li>
      <li>
        <code>nexus_site</code> — set only while the site is closed to the public, after you
        enter the site password, so you are not asked for it on every page. It holds an
        expiry and a signature, never the password itself, and lasts 30 days.
      </li>
    </ul>
    <p>
      Both are first-party: they are set by this site, read by this site, and sent nowhere
      else. Neither identifies you to anybody outside it.
    </p>

    <h2>Local storage</h2>
    <p>
      Four values, kept by your browser and never sent to the server. They hold display
      preferences, not identity, and there is nothing in them worth anyone reading.
    </p>
    <ul>
      <li>
        <code>nexus-theme</code> — whether you chose light, dark, or your system setting.
      </li>
      <li>
        <code>nexus-module</code> — which module you were last in, so a reload lands where
        you left off.
      </li>
      <li>
        <code>nexus-library-view</code> — whether your library shows as a grid or a table.
      </li>
      <li>
        <code>nexus.home.folded.*</code> — which sections of the home page you collapsed, one
        entry per module.
      </li>
    </ul>

    <h2>The bot check</h2>
    <p>
      The sign-up and password-reset forms, and no other page, load Cloudflare Turnstile to
      tell a person from a script. It runs in a frame served by Cloudflare, and anything it
      stores is set by Cloudflare on its own domain under its own policy — this site sets
      nothing on its behalf and receives nothing from it but a pass or a fail. What that
      discloses to Cloudflare, and on what basis, is set out in the{' '}
      <Link to="/privacy">Privacy Policy</Link>.
    </p>

    <h2>The app</h2>
    <p>
      The NexusLibrary mobile app sets no cookies. It keeps its sign-in token in the
      platform keychain instead, which is why it is never shown this page.
    </p>

    <h2>Removing them</h2>
    <p>
      Clearing this site's data in your browser removes all of it. You will be signed out,
      your display preferences will return to their defaults, and if the site is still closed
      to the public you will be asked for its password again. Blocking cookies entirely will
      stop you staying signed in; nothing else depends on them.
    </p>

    <h2>Changes</h2>
    <p>
      If what the site stores changes, this page changes with it and you will be asked to
      acknowledge the new version when you next sign in. The date at the top says when it
      last changed.
    </p>

    <p>
      Questions about any of this go to [CONTACT EMAIL]. How your data is handled more
      generally is set out in the <Link to="/privacy">Privacy Policy</Link>.
    </p>
  </LegalLayout>
)
