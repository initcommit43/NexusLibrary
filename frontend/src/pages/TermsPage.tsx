import { Link } from 'react-router-dom'
import { LegalLayout } from '../components/LegalLayout'

/*
 * TODO before this goes public: [BRACKETED] values are placeholders, and the governing-law
 * clause in particular should match wherever the operator actually is.
 */

/**
 * The terms a reader accepts when they register.
 *
 * <p>Short on purpose. A hobby project asking people to agree to ten pages they will not
 * read buys nothing; what it owes them is a plain account of what the service promises,
 * which is not much, and what it expects, which is less.
 */
export const TermsPage = () => (
  <LegalLayout title="Terms of Service" updated="7 September 2026">
    <p>
      By creating an account you agree to these terms. If you do not, please do not register.
    </p>

    <h2>What this is</h2>
    <p>
      NexusLibrary is a personal media tracker, run as an independent project. It is provided
      free of charge, as is, with no warranty of any kind and no guarantee that it will be
      available, correct, or still running tomorrow. It is not a backup service: keep your
      own copy of anything you would mind losing. Settings offers a full export at any time.
    </p>

    <h2>Your account</h2>
    <p>
      You are responsible for what happens under your account and for keeping your password
      to yourself. Use an address you actually control — it is the only way back in if you
      forget your password. One account per person. Tell us at [CONTACT EMAIL] if you think
      someone else has got into your account.
    </p>
    <p>
      You must be at least 16 years old to register.
    </p>

    <h2>What you may not do</h2>
    <p>
      Do not use the service to break the law; do not try to reach another user's data; do
      not attack, overload, scrape or work around the rate limits; do not attempt to
      redistribute the metadata this app receives from its providers, which is theirs and not
      ours to give away.
    </p>

    <h2>Your content</h2>
    <p>
      Your notes, ratings and reviews are yours. We store and display them back to you in
      order to run the service, and claim no ownership of them. Deleting them, or your
      account, removes them.
    </p>

    <h2>Other services</h2>
    <p>
      Catalogue data comes from third parties listed on the{' '}
      <Link to="/credits">Credits</Link> page, and connecting an external account means using
      that service under its own terms as well as these. We do not control what those
      services do, whether they stay available, or whether their data is accurate.
    </p>

    <h2>Ending it</h2>
    <p>
      You can delete your account at any time in Settings, which takes effect immediately. We
      may suspend or remove an account that breaks these terms, or stop running the service
      altogether — with as much warning as circumstances allow.
    </p>

    <h2>Liability</h2>
    <p>
      Liability is limited to intent and gross negligence, and to damages arising from injury
      to life, body or health, or from the breach of an obligation essential to the contract.
      Nothing here limits liability where the law does not permit it to be limited.
    </p>

    <h2>Changes</h2>
    <p>
      These terms may change. If they change materially you will be asked to accept the new
      version when you next sign in. The date at the top says when they last changed.
    </p>

    <h2>Governing law</h2>
    <p>
      [GOVERNING LAW], excluding its conflict-of-laws rules, and without removing any
      protection that the mandatory law of your country of residence gives you.
    </p>

    <p>
      How your data is handled is set out separately in the{' '}
      <Link to="/privacy">Privacy Policy</Link>, which forms part of these terms.
    </p>
  </LegalLayout>
)
