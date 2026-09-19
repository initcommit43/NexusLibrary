import { Link } from 'react-router-dom'
import { LegalLayout } from '../components/LegalLayout'

/*
 * TODO before the app is submitted: [BRACKETED] values are placeholders, as on the other
 * legal pages, and [OPERATOR] here must be the same legal person named in the Privacy
 * Policy and in App Store Connect — a licensor who does not match the seller on the listing
 * is a review rejection.
 */

/**
 * The licence for the mobile app.
 *
 * <p>Native only. A browser is never offered this document — see {@code Agreement} on the
 * backend, which is what decides that. It is served on the web all the same, because the
 * store listing has to link to a public URL and a reader of the app has to be able to reach
 * it from a phone that is not signed in.
 *
 * <p>Separate from the Terms rather than folded into them: this covers the copy of the
 * software on a device, the Terms cover the service it talks to, and Apple requires the
 * minimum terms in the last section below of any app that does not use their standard EULA.
 */
export const EulaPage = () => (
  <LegalLayout title="End User Licence Agreement" updated="19 September 2026">
    <p>
      This agreement covers the NexusLibrary mobile app. It is between you and [OPERATOR],
      who licenses the app to you — it is not a sale, and nothing in it transfers ownership
      of the software. Using the service the app talks to is covered separately by the{' '}
      <Link to="/terms">Terms of Service</Link> and the{' '}
      <Link to="/privacy">Privacy Policy</Link>, both of which apply as well.
    </p>

    <h2>Your licence</h2>
    <p>
      You get a personal, non-exclusive, non-transferable, revocable licence to install and
      use the app on any device you own or control, as permitted by the usage rules of the
      store you installed it from. The app is free of charge. The licence lasts as long as
      you keep to this agreement.
    </p>

    <h2>What you may not do</h2>
    <p>
      Do not sell, rent, sublicense or redistribute the app; do not remove or obscure any
      notice in it; do not use it to reach another user's data or to attack, overload or work
      around the limits of the service behind it. Do not reverse-engineer, decompile or
      disassemble it, except so far as the law expressly allows despite this sentence — in
      the EU that includes the interoperability case in Art. 6 of Directive 2009/24/EC, and
      nothing here is meant to take that away.
    </p>

    <h2>Updates</h2>
    <p>
      Updates may be offered through the store and may change or remove features. An older
      build may stop working when the service it talks to changes: the app is a client for a
      service that moves, and a version too old to be served will say so rather than fail
      quietly.
    </p>

    <h2>Other people's content</h2>
    <p>
      Catalogue data — covers, descriptions, artwork — comes from the third parties listed on
      the <Link to="/credits">Credits</Link> page. It is theirs, it is shown to you under
      their terms, and this licence gives you no rights in it.
    </p>

    <h2>Ending it</h2>
    <p>
      The licence ends if you break this agreement, and you may end it at any time by
      deleting the app. Deleting the app does not delete your account: that is done in
      Settings, in the app or on the website.
    </p>

    <h2>No warranty</h2>
    <p>
      The app is provided as is, with no warranty of any kind, and with no promise that it
      will be available, correct, uninterrupted or still running tomorrow. It is not a backup
      service — keep your own copy of anything you would mind losing, which Settings will
      export for you at any time.
    </p>

    <h2>Liability</h2>
    <p>
      Liability is limited to intent and gross negligence, and to damages arising from injury
      to life, body or health, or from the breach of an obligation essential to the contract.
      Nothing here limits liability where the law does not permit it to be limited, and
      nothing here removes a protection that the mandatory law of your country of residence
      gives you.
    </p>

    <h2>If you installed from the App Store</h2>
    <p>
      The following applies where Apple is the store, and Apple requires it to be said
      plainly:
    </p>
    <ul>
      <li>
        This agreement is between you and [OPERATOR] only, not with Apple. [OPERATOR], not
        Apple, is solely responsible for the app and its content.
      </li>
      <li>
        Apple has no obligation to furnish any maintenance or support for the app.
      </li>
      <li>
        If the app fails to conform to any applicable warranty, you may notify Apple, and
        Apple will refund the purchase price to you. The app is free, so there is nothing to
        refund. To the maximum extent permitted by law, Apple has no other warranty
        obligation with respect to the app.
      </li>
      <li>
        [OPERATOR], not Apple, is responsible for addressing any claim by you or a third
        party relating to the app or your use of it — including product liability claims, any
        claim that the app fails to conform to a legal or regulatory requirement, and claims
        under consumer protection or similar legislation.
      </li>
      <li>
        [OPERATOR], not Apple, is responsible for the investigation, defence, settlement and
        discharge of any third-party claim that the app infringes that party's intellectual
        property rights.
      </li>
      <li>
        You represent that you are not located in a country subject to a U.S. Government
        embargo or designated as a terrorist-supporting country, and that you are not on any
        U.S. Government list of prohibited or restricted parties.
      </li>
      <li>
        Apple and its subsidiaries are third-party beneficiaries of this agreement, and on
        your acceptance of it Apple will have the right to enforce it against you as a
        third-party beneficiary.
      </li>
    </ul>

    <h2>If you installed from Google Play</h2>
    <p>
      Google is not a party to this agreement and is not responsible for the app. Google
      Play's own terms apply to your use of the store.
    </p>

    <h2>Changes</h2>
    <p>
      This agreement may change. If it changes materially you will be asked to accept the new
      version when you next open the app. The date at the top says when it last changed.
    </p>

    <h2>Governing law</h2>
    <p>
      [GOVERNING LAW], excluding its conflict-of-laws rules, and without removing any
      protection that the mandatory law of your country of residence gives you.
    </p>

    <p>Questions about this licence go to [CONTACT EMAIL].</p>
  </LegalLayout>
)
