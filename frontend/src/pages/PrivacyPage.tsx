import { Link } from 'react-router-dom'
import { LegalLayout } from '../components/LegalLayout'

/*
 * TODO before this goes public: every [BRACKETED] value below is a placeholder. GDPR Art. 13
 * requires the controller to be identifiable, so the name and contact address have to be
 * real before anyone can register. They are left bracketed rather than guessed at — a
 * privacy policy naming the wrong controller is worse than one that is obviously unfinished.
 */

/**
 * What the app holds about a reader, why, and what they can do about it.
 *
 * <p>Written against what the code actually stores rather than from a template. Every table
 * named here exists, and the retention periods are the ones the app really applies.
 */
export const PrivacyPage = () => (
  <LegalLayout title="Privacy Policy" updated="14 September 2026">
    <p>
      NexusLibrary is a media tracker. It keeps the list of things you are reading, watching
      and playing, and it needs an account to know whose list is whose. This page says
      exactly what that means for your data.
    </p>

    <h2>Who is responsible</h2>
    <p>
      The controller for the purposes of the GDPR is [CONTROLLER NAME], [POSTAL ADDRESS].
      Questions about your data, or any of the rights below, go to [CONTACT EMAIL].
    </p>

    <h2>What is stored, and why</h2>

    <h3>Your account</h3>
    <p>
      Your email address, your username, and your password — the password only ever as a
      bcrypt hash, which cannot be read back. The email address is how you sign in and how a
      password reset link would reach you. Legal basis: performance of the contract you enter
      into by creating an account, Art. 6(1)(b) GDPR.
    </p>
    <p>
      Your date of birth, given when you register. An account made before registration asked
      for it can add it once in Settings. It is stored as the date rather than as an age, and
      it cannot be changed afterwards from the app; to correct a mistake, write to us. It is
      used for two things only: refusing accounts under 16, and deciding whether the 18+
      content setting is available to you. It is never shown on your profile and no proof of
      it is asked for. It is included in your data export and deleted with your account.
      Legal basis: Art. 6(1)(c) GDPR for the youth-protection duties of the JMStV, and our
      legitimate interest in running the service lawfully, Art. 6(1)(f) GDPR.
    </p>
    <p>
      Your content settings: whether 18+ titles are shown to you and whether their covers are
      blurred. Legal basis: Art. 6(1)(b) GDPR.
    </p>

    <h3>Your library</h3>
    <p>
      What you track and everything you record about it: status, rating, progress, start and
      finish dates, private notes, favourites, reviews you write, and a log of the changes
      you make so the activity feed and the statistics pages have something to show. This is
      the service itself. Legal basis: Art. 6(1)(b) GDPR.
    </p>

    <h3>Connected accounts</h3>
    <p>
      If you connect Steam, AniList, MyAnimeList or Simkl, we store your identifier on that
      service and the access token it issued, so your library can be imported and kept in
      step. Tokens are encrypted at rest with AES-GCM and are never included in a data
      export. Connecting is entirely your choice and nothing else in the app depends on it.
      Legal basis: your consent, Art. 6(1)(a) GDPR, withdrawn at any time by disconnecting
      the account in Settings, which deletes the stored tokens.
    </p>

    <h3>Sessions</h3>
    <p>
      When you sign in, a record of that session is stored so it can be ended again — its
      identifier, which kind of client it belongs to, when it was issued and when it
      expires. Sessions expire after 30 days and are deleted after that. Signing out, signing
      out everywhere, changing your password or resetting it all revoke them immediately.
      Legal basis: Art. 6(1)(b) GDPR.
    </p>

    <h3>Your IP address</h3>
    <p>
      Your IP address is used to count requests to the sign-in, registration and
      password-reset endpoints, so that nobody can guess passwords at speed. The count is
      held in memory for one minute at a time and then discarded. It is never written to the
      database and never associated with your account. Legal basis: our legitimate interest
      in keeping accounts from being broken into, Art. 6(1)(f) GDPR.
    </p>

    <h3>The bot check</h3>
    <p>
      The sign-up and password-reset forms run Cloudflare Turnstile, which is what keeps them
      from being filled in automatically. It loads a script from Cloudflare and sends
      Cloudflare your IP address together with the token the challenge produces, so that
      Cloudflare can confirm a browser really solved it. Cloudflare states that Turnstile is
      not used to track visitors across sites and that its data is not used for advertising.
      On our side nothing is kept but the answer: passed, or not. It runs on those two forms
      and on no other page. Legal basis: our legitimate interest in keeping automated sign-ups
      and reset requests out, Art. 6(1)(f) GDPR.
    </p>

    <h3>Server logs</h3>
    <p>
      The server records errors and warnings so faults can be diagnosed. Reset links,
      passwords, tokens and encryption keys are deliberately never logged. Legal basis:
      Art. 6(1)(f) GDPR.
    </p>

    <h2>What is not collected</h2>
    <p>
      There is no analytics, no tracking, no advertising, no profiling and no automated
      decision-making within the meaning of Art. 22 GDPR. The only third-party script on this
      site is the Cloudflare Turnstile bot check described above, which loads on the sign-up
      and password-reset forms and nowhere else. Your data is not sold, rented or shared for
      anyone else's purposes.
    </p>

    <h2>Cookies and local storage</h2>
    <p>
      One cookie, and three values kept in your browser's local storage. All four are needed
      for the site to work as you asked it to, so none of them requires consent under
      § 25(2) TDDDG:
    </p>
    <ul>
      <li>
        <code>nexus_refresh</code> — the cookie that keeps you signed in. It is httpOnly, so
        no script can read it, restricted to this site, and scoped to the sign-in endpoints
        alone. It lasts 30 days, or until you sign out.
      </li>
      <li>
        <code>nexus.theme</code> — whether you chose light, dark or your system setting.
      </li>
      <li>
        <code>nexus.module</code> — which module you were last in, so a reload lands where
        you left.
      </li>
      <li>
        <code>nexus.folded.*</code> — which sections of the home page you collapsed.
      </li>
    </ul>
    <p>
      The last three never leave your browser. Clearing your site data removes all four; you
      will be signed out and the display preferences will return to their defaults.
    </p>

    <h2>Who else sees it</h2>
    <p>
      The application and its database run on Railway, which processes data on our
      instructions as a processor under Art. 28 GDPR. Railway is based in the United States,
      so hosting involves a transfer outside the EEA, made on the safeguards in Art. 46 GDPR.
    </p>
    <p>
      Opening the sign-up or password-reset form loads the bot check described above, which
      discloses your IP address to Cloudflare. Cloudflare processes it on our instructions as
      a processor under Art. 28 GDPR, and is likewise based in the United States, so this too
      is a transfer outside the EEA made on the safeguards in Art. 46 GDPR. No other page
      contacts Cloudflare.
    </p>
    <p>
      When you search or open a title, a request goes to the relevant metadata provider —
      IGDB, TMDB, AniList, Simkl or Open Library. These requests are made by our server, not
      by your browser, so your IP address is not disclosed to them. When you connect an
      account, we exchange data with that service on your behalf, under their privacy policy
      as well as this one. They are listed on the <Link to="/credits">Credits</Link> page.
    </p>

    <h2>How long it is kept</h2>
    <p>
      Your account and everything attached to it are kept until you delete the account.
      Deleting it removes your entries, reviews, activity, notifications, preferences,
      connected accounts and stored tokens, immediately and permanently. Sessions expire
      after 30 days; password reset links after 30 minutes.
    </p>

    <h2>Your rights</h2>
    <p>
      You have the right of access (Art. 15), rectification (Art. 16), erasure (Art. 17),
      restriction of processing (Art. 18), data portability (Art. 20) and objection
      (Art. 21). Where processing rests on consent, you may withdraw it at any time under
      Art. 7(3), without affecting what was lawful before.
    </p>
    <p>
      Two of these you can exercise yourself, without asking anyone: Settings offers a full
      export of your data as a file, and account deletion. Everything else goes to
      [CONTACT EMAIL].
    </p>
    <p>
      You may also complain to a supervisory authority under Art. 77 GDPR — for us,
      [SUPERVISORY AUTHORITY].
    </p>

    <h2>Age</h2>
    <p>
      This service is not intended for anyone under 16, and registration refuses a date of
      birth below that. If you believe an account here belongs to someone under 16, write to
      [CONTACT EMAIL] and we will delete it.
    </p>

    <h2>Changes</h2>
    <p>
      If this policy changes in a way that affects you, you will be asked to read and accept
      the new version the next time you sign in. The date at the top says when it last
      changed.
    </p>
  </LegalLayout>
)
