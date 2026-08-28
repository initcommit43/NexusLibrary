import { Link } from 'react-router-dom'
import type { SearchResult, TrackingStatus } from '../api/client'
import { mediaPathFor } from '../modules/registry'
import { AddToShelfMenu } from './AddToShelfMenu'

interface Props {
  result: SearchResult
  state: 'idle' | 'saving' | 'tracked'
  onTrack: (status: TrackingStatus) => void
}

/**
 * One catalogue result, as search and browse both show it: a cover, a title, a year, and the
 * menu that puts it on a shelf.
 *
 * <p>Distinct from the library's own card, which shows progress and a rating for something
 * already tracked. This one is for a title you do not have yet.
 *
 * <p>The cover and the title open the title's page; the shelf menu sits over the cover rather
 * than inside the link, so the one place a click does something else is the one place it
 * looks like it would.
 */
export const CatalogCard = ({ result, state, onTrack }: Props) => {
  const path = mediaPathFor(result)

  return (
    <article className="card cover-card">
      <div className="cover-art">
        <Link className="cover-link" to={path} aria-label={result.title}>
          {result.coverUrl ? (
            <img src={result.coverUrl} alt="" loading="lazy" />
          ) : (
            <div className="cover-placeholder" aria-hidden="true" />
          )}
        </Link>

        <AddToShelfMenu result={result} state={state} onAdd={onTrack} />
      </div>

      <div className="cover-body">
        <h2>
          <Link to={path}>{result.title}</Link>
        </h2>
        <p className="muted">{result.releaseDate?.slice(0, 4) ?? 'Unreleased'}</p>
      </div>
    </article>
  )
}
