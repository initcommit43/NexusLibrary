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

        <div className="card-actions corner-top">
          <button
            type="button"
            className="card-action"
            aria-label={`Edit ${entry.title}`}
            onClick={onEdit}
          >
            <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" aria-hidden>
              <path d="M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17v3Z" strokeWidth="1.8" strokeLinejoin="round" />
            </svg>
            <span className="card-action-label">Edit</span>
          </button>
        </div>
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
            {progress && <span>{progress}</span>}
            {/* Pushed to the far side itself, so a rating without a count still sits there. */}
            {score && <span className="cover-score">{score}</span>}
          </p>
        )}
      </div>
    </article>
  )
}
