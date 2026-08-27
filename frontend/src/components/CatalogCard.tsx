import type { SearchResult, TrackingStatus } from '../api/client'
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
 */
export const CatalogCard = ({ result, state, onTrack }: Props) => (
  <article className="card cover-card">
    <div className="cover-art">
      {result.coverUrl ? (
        <img src={result.coverUrl} alt="" loading="lazy" />
      ) : (
        <div className="cover-placeholder" aria-hidden="true" />
      )}

      <AddToShelfMenu result={result} state={state} onAdd={onTrack} />
    </div>

    <div className="cover-body">
      <h2>{result.title}</h2>
      <p className="muted">{result.releaseDate?.slice(0, 4) ?? 'Unreleased'}</p>
    </div>
  </article>
)
