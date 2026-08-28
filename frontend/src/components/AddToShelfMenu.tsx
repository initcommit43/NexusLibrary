import type { SearchResult, TrackingStatus } from '../api/client'
import { statusLabelsFor, typeDefinitionFor } from '../modules/registry'
import { STATUS_ORDER } from './trackingStatus'
import { useMenuDismiss } from './useMenuDismiss'

const PlusIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <path d="M12 5v14M5 12h14" strokeWidth="2" strokeLinecap="round" />
  </svg>
)

interface Props {
  result: SearchResult
  state: 'idle' | 'saving' | 'tracked'
  onAdd: (status: TrackingStatus) => void
}

/**
 * Puts a catalogue result on a shelf, choosing which shelf in the same click.
 *
 * <p>The same menu the library uses to move an entry between statuses, so adding something
 * and re-shelving it later are one gesture rather than two. The status words come from the
 * media type — you plan to watch a film and to read a book.
 */
export const AddToShelfMenu = ({ result, state, onAdd }: Props) => {
  const { open, setOpen, container } = useMenuDismiss<HTMLDivElement>()

  const labels = statusLabelsFor(result.mediaType)
  const order = typeDefinitionFor(result.mediaType)?.statusOrder ?? STATUS_ORDER
  const tracked = state === 'tracked'

  return (
    <div className="add-menu" ref={container}>
      <button
        type="button"
        className="card-menu"
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={state !== 'idle'}
        aria-label={
          tracked ? `${result.title} is already tracked` : `Add ${result.title} to a shelf`
        }
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <PlusIcon />
      </button>

      {open && (
        <ul className="status-options" role="menu">
          {order.map((status) => (
            <li key={status} role="none">
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setOpen(false)
                  onAdd(status)
                }}
              >
                Add as {labels[status].toLowerCase()}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
