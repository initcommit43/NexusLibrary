import { useState } from 'react'
import type { SearchResult, TrackingStatus } from '../api/client'
import { statusLabelsFor } from '../modules/registry'

const PencilIcon = () => (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" aria-hidden>
    <path d="M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17v3Z" strokeWidth="1.8" strokeLinejoin="round" />
  </svg>
)

const PlayIcon = () => (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="currentColor" aria-hidden>
    <path d="M8 5.5v13l11-6.5z" />
  </svg>
)

const CalendarIcon = () => (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" aria-hidden>
    <rect x="4" y="5.5" width="16" height="15" rx="2.5" strokeWidth="1.8" />
    <path d="M4 10h16M9 3.5v4M15 3.5v4" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
)

interface Props {
  result: SearchResult
  state: 'idle' | 'saving' | 'tracked'
  onAdd: (status: TrackingStatus) => void
  onEdit: () => void
}

/**
 * Puts a catalogue result on a shelf, from the corner of its cover.
 *
 * <p>Three deep rather than one wide. The pencil is what a hovered card offers, and the two
 * shelves nearly every title goes to — the one you are on and the one you mean to get to —
 * rise above it when it is reached for.
 *
 * <p>The pencil opens the editor, which is where the remaining shelves are along with a
 * score and a progress count. A menu here would have offered the same two statuses the
 * shortcuts already are, plus three nobody sets from a shelf of forty covers.
 */
export const AddToShelfMenu = ({ result, state, onAdd, onEdit }: Props) => {
  // Held here rather than left to a :hover rule. The stack has two states across two
  // elements, and in CSS they fought each other over which one a class had reached first.
  const [reaching, setReaching] = useState(false)

  const labels = statusLabelsFor(result.mediaType)
  // Only while a save is in flight. Being on a shelf already is not a reason to refuse the
  // control: moving a title from one shelf to another is the same gesture as putting it on
  // the first, and this is where someone standing on the browse page reaches for it.
  const busy = state === 'saving'

  return (
    <div
      className="card-actions"
      data-reaching={reaching ? '' : undefined}
      onMouseEnter={() => setReaching(true)}
      onMouseLeave={() => setReaching(false)}
      onFocus={() => setReaching(true)}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setReaching(false)
      }}
    >
      <button
        type="button"
        className="card-action quick"
        disabled={busy}
        aria-label={`Set ${result.title} as ${labels.IN_PROGRESS}`}
        onClick={() => onAdd('IN_PROGRESS')}
      >
        <PlayIcon />
        <span className="card-action-label">Set as {labels.IN_PROGRESS}</span>
      </button>

      <button
        type="button"
        className="card-action quick"
        disabled={busy}
        aria-label={`Set ${result.title} as ${labels.PLANNING}`}
        onClick={() => onAdd('PLANNING')}
      >
        <CalendarIcon />
        <span className="card-action-label">Set as {labels.PLANNING}</span>
      </button>

      <button
        type="button"
        className="card-action"
        disabled={busy}
        aria-label={`Edit ${result.title}`}
        onClick={onEdit}
      >
        <PencilIcon />
        <span className="card-action-label">Edit</span>
      </button>
    </div>
  )
}
