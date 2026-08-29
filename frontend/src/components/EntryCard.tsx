import { Link } from 'react-router-dom'
import type { TrackedItem } from '../api/client'
import { detailPathFor } from '../modules/registry'
import { toDisplayScore } from './rating'
import { episodesWaiting, progressSummary } from './progress'

/**
 * Cover, title, and the two numbers worth seeing on a shelf. Everything editable lives
 * behind the button that appears on hover, so forty cards are forty covers rather than
 * forty forms.
 *
 * <p>A card without {@code onEdit} carries no button at all, for the places where the cards
 * themselves are the thing being handled and a control on each is in the way.
 */
export const EntryCard = ({ entry, onEdit }: { entry: TrackedItem; onEdit?: () => void }) => {
  const progress = progressSummary(entry)
  const waiting = episodesWaiting(entry)
  const score = toDisplayScore(entry.rating)
  const to = detailPathFor(entry)

  return (
    /*
     * The frame exists so the corner mark can stand on the card's edge. The card itself clips
     * — that is what keeps the artwork inside its rounded border — so anything meant to cross
     * that edge has to hang outside it rather than in it.
     */
    <div className="cover-frame">
      {/*
        * Top left, where a corner mark is looked for. Every airing title carries one and they
        * are all the same size: the number of episodes waiting where there are any, and the
        * bare mark where you are caught up. A mark that changed size with its state read as a
        * mark that had gone wrong.
        */}
      {waiting !== null && (
        <span
          className="airing-mark"
          title={waiting > 0 ? `${waiting} waiting to watch` : 'Airing, and you are caught up'}
        >
          {waiting > 0 ? waiting : ''}
        </span>
      )}

      <article className="card cover-card">
      <div className="cover-art">
        <Link className="cover-link" to={to} title={entry.title}>
          {entry.coverUrl ? (
            <img src={entry.coverUrl} alt="" loading="lazy" />
          ) : (
            <div className="cover-placeholder" aria-hidden="true" />
          )}
        </Link>

        {onEdit && (
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
        )}
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
    </div>
  )
}
