import type { SearchResult } from '../api/client'

interface Props {
  result: SearchResult
  state: 'idle' | 'saving' | 'tracked'
  onTrack: () => void
}

/**
 * One catalogue result, as search and browse both show it: a cover, a title, a year, and the
 * one button that puts it on a shelf.
 *
 * <p>Distinct from the library's own card, which shows progress and a rating for something
 * already tracked. This one is for a title you do not have yet.
 */
export const CatalogCard = ({ result, state, onTrack }: Props) => (
  <article className="card cover-card">
    {result.coverUrl ? (
      <img src={result.coverUrl} alt="" loading="lazy" />
    ) : (
      <div className="cover-placeholder" aria-hidden="true" />
    )}
    <div className="cover-body">
      <h2>{result.title}</h2>
      <p className="muted">{result.releaseDate?.slice(0, 4) ?? 'Unreleased'}</p>
      <button
        type="button"
        className={state === 'tracked' ? 'ghost' : ''}
        disabled={state !== 'idle'}
        onClick={onTrack}
      >
        {state === 'saving' ? 'Saving…' : state === 'tracked' ? 'Tracked' : 'Track'}
      </button>
    </div>
  </article>
)
