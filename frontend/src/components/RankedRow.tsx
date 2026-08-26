import type { SearchResult } from '../api/client'

interface Props {
  result: SearchResult
  rank: number
  state: 'idle' | 'saving' | 'tracked'
  onTrack: () => void
}

const facetText = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() !== '' ? value : null

const facetList = (value: unknown): string[] => (Array.isArray(value) ? value.map(String) : [])

/**
 * One row of a top-100 shelf: a rank, a small cover, and the facts a reader ranks by.
 *
 * <p>A different shape to {@link CatalogCard} on purpose. A ranked list is read down rather
 * than across, and the score and format that justify the ranking are the whole point of it —
 * as a row of covers it would say nothing about why anything is where it is.
 */
export const RankedRow = ({ result, rank, state, onTrack }: Props) => {
  const score = typeof result.facets?.score === 'number' ? result.facets.score : null
  const format = facetText(result.facets?.format)
  const genres = facetList(result.facets?.genres)
  const episodes = typeof result.facets?.episodes === 'number' ? result.facets.episodes : null
  const chapters = typeof result.facets?.chapters === 'number' ? result.facets.chapters : null

  return (
    <article className="card ranked-row">
      <span className="ranked-position" aria-label={`Number ${rank}`}>
        #{rank}
      </span>

      {result.coverUrl ? (
        <img className="ranked-cover" src={result.coverUrl} alt="" loading="lazy" />
      ) : (
        <div className="ranked-cover cover-placeholder" aria-hidden="true" />
      )}

      <div className="ranked-main">
        <h3>{result.title}</h3>
        {genres.length > 0 && (
          <ul className="ranked-genres">
            {genres.map((genre) => (
              <li key={genre}>{genre}</li>
            ))}
          </ul>
        )}
      </div>

      <div className="ranked-facts">
        {score !== null && <span className="ranked-score">{score}%</span>}
        {format && <span className="muted">{format}</span>}
        {episodes !== null && <span className="muted">{episodes} episodes</span>}
        {chapters !== null && <span className="muted">{chapters} chapters</span>}
        {result.releaseDate && <span className="muted">{result.releaseDate.slice(0, 4)}</span>}
      </div>

      <button
        type="button"
        className={state === 'tracked' ? 'ghost' : ''}
        disabled={state !== 'idle'}
        onClick={onTrack}
      >
        {state === 'saving' ? 'Saving…' : state === 'tracked' ? 'Tracked' : 'Track'}
      </button>
    </article>
  )
}
