import { Link } from 'react-router-dom'
import type { TrackedItem } from '../api/client'
import { detailPathFor } from '../modules/registry'
import { toDisplayScore } from './rating'
import { MINUTES_PER_HOUR } from './progress'

/** "8 / 13", "128 h", or nothing when there is no progress worth showing. */
const progressLabel = (entry: TrackedItem): string | null => {
  if (entry.progressCurrent === null) return null
  if (entry.progressUnit === 'MINUTES') {
    return `${Math.round(entry.progressCurrent / MINUTES_PER_HOUR)} h`
  }
  return entry.progressMax ? `${entry.progressCurrent} / ${entry.progressMax}` : `${entry.progressCurrent}`
}

/**
 * Cover, title, and the two numbers worth seeing on a shelf. Everything editable lives
 * behind the button that appears on hover, so forty cards are forty covers rather than
 * forty forms.
 */
export const EntryCard = ({ entry, onEdit }: { entry: TrackedItem; onEdit: () => void }) => {
  const progress = progressLabel(entry)
  const score = toDisplayScore(entry.rating)
  const to = detailPathFor(entry)

  return (
    <article className="card cover-card">
      <div className="cover-art">
        <Link className="cover-link" to={to} title={entry.title}>
          {entry.coverUrl ? (
            <img src={entry.coverUrl} alt="" loading="lazy" />
          ) : (
            <div className="cover-placeholder" aria-hidden="true" />
          )}
        </Link>

        <button
          type="button"
          className="card-menu"
          aria-label={`Edit ${entry.title}`}
          onClick={onEdit}
        >
          <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden>
            <circle cx="5" cy="12" r="1.6" />
            <circle cx="12" cy="12" r="1.6" />
            <circle cx="19" cy="12" r="1.6" />
          </svg>
        </button>
      </div>

      <div className="cover-heading">
        <h3>
          {/* The clamp cuts long titles, so the full one is a hover away. */}
          <Link to={to} title={entry.title}>
            {entry.title}
          </Link>
        </h3>
        {(progress || score) && (
          <p className="cover-stats">
            <span>{progress}</span>
            <span>{score}</span>
          </p>
        )}
      </div>
    </article>
  )
}
