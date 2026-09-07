import { LegalLayout } from '../components/LegalLayout'
import { CREDITS, FONT_CREDITS } from '../legal/credits'

/*
 * TODO before this goes public: TMDB's terms require their logo alongside the notice, from
 * their approved set at themoviedb.org/about/logos-attribution, and it must be less
 * prominent than the NexusLibrary mark. The wording below is the half that is mandatory;
 * the asset still has to be downloaded into /public and placed here.
 */

/**
 * Where everything on the shelves came from.
 *
 * <p>Not a courtesy page. Four of these providers require attribution by contract, and
 * TMDB's wording is quoted rather than written — see {@link CREDITS}.
 */
export const CreditsPage = () => (
  <LegalLayout title="Credits">
    <p>
      NexusLibrary holds no catalogue of its own. Every title, cover, release date and
      synopsis comes from one of the services below, and each of them made that data
      available on terms this page is part of keeping.
    </p>

    <dl className="credit-list">
      {CREDITS.map((credit) => (
        <div key={credit.name} className="credit">
          <dt>
            <a href={credit.url} rel="noreferrer">
              {credit.name}
            </a>
          </dt>
          <dd>
            <p>{credit.use}</p>
            {credit.required && <p className="credit-notice">{credit.required}</p>}
          </dd>
        </div>
      ))}
    </dl>

    <h2>Typefaces</h2>
    <p>
      {FONT_CREDITS.map((font, index) => (
        <span key={font.name}>
          {index > 0 && ' and '}
          <a href={font.url} rel="noreferrer">
            {font.name}
          </a>
          {` (${font.licence})`}
        </span>
      ))}
      .
    </p>

    <h2>Trademarks</h2>
    <p>
      All product names, logos and brands are the property of their owners. Naming a service
      here identifies where data came from; it does not imply that the service endorses,
      certifies or is affiliated with NexusLibrary.
    </p>
  </LegalLayout>
)
