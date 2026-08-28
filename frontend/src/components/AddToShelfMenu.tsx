import type { SearchResult, TrackingStatus } from '../api/client'
import { statusLabelsFor, typeDefinitionFor } from '../modules/registry'
import { STATUS_ORDER } from './trackingStatus'
import { useMenuDismiss } from './useMenuDismiss'

const PencilIcon = () => (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" aria-hidden>
    <path
      d="M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17v3Z"
      strokeWidth="1.8"
      strokeLinejoin="round"
    />
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
}

/**
 * Puts a catalogue result on a shelf, from the corner of its cover.
 *
 * <p>Three deep rather than one wide. The pencil is what a hovered card offers, and the two
 * shelves nearly every title goes to — the one you are on and the one you mean to get to —
 * rise above it when it is reached for. Everything else is still in the menu behind it, so
 * the two shortcuts cost nothing to anyone who wants a different shelf.
 */
export const AddToShelfMenu = ({ result, state, onAdd }: Props) => {
  const { open, setOpen, container } = useMenuDismiss<HTMLDivElement>()

  const labels = statusLabelsFor(result.mediaType)
  const order = typeDefinitionFor(result.mediaType)?.statusOrder ?? STATUS_ORDER
  const busy = state !== 'idle'

  const add = (status: TrackingStatus) => {
    setOpen(false)
    onAdd(status)
  }

  return (
    <div className="card-actions" ref={container}>
      <button
        type="button"
        className="card-action quick"
        disabled={busy}
        title={labels.IN_PROGRESS}
        aria-label={`Add ${result.title} to ${labels.IN_PROGRESS}`}
        onClick={() => add('IN_PROGRESS')}
      >
        <PlayIcon />
      </button>

      <button
        type="button"
        className="card-action quick"
        disabled={busy}
        title={labels.PLANNING}
        aria-label={`Add ${result.title} to ${labels.PLANNING}`}
        onClick={() => add('PLANNING')}
      >
        <CalendarIcon />
      </button>

      <button
        type="button"
        className="card-action"
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={busy}
        title="Add to a shelf"
        aria-label={`Add ${result.title} to a shelf`}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <PencilIcon />
      </button>

      {open && (
        <ul className="status-options" role="menu">
          {order.map((status) => (
            <li key={status} role="none">
              <button type="button" role="menuitem" onClick={() => add(status)}>
                {labels[status]}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
