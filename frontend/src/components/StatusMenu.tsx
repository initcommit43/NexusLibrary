import { useState } from 'react'
import { ApiError, api, type TrackedItem, type TrackingStatus } from '../api/client'
import { statusLabelsFor, typeDefinitionFor } from '../modules/registry'
import { STATUS_ORDER } from './trackingStatus'
import { useMenuDismiss } from './useMenuDismiss'

const ChevronIcon = () => (
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 9 6 6 6-6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

/**
 * The status an entry is on, and a menu to move it somewhere else.
 *
 * <p>Moving something between lists is the commonest thing anyone does here, so it is one
 * click rather than a trip through the editor — which stays at the bottom of the menu for
 * everything a status cannot express.
 */
export const StatusMenu = ({
  entry,
  onChanged,
  onOpenEditor,
}: {
  entry: TrackedItem
  onChanged: () => void
  onOpenEditor: () => void
}) => {
  const { open, setOpen, container } = useMenuDismiss<HTMLDivElement>()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const labels = statusLabelsFor(entry.mediaType)
  const order = typeDefinitionFor(entry.mediaType)?.statusOrder ?? STATUS_ORDER

  const moveTo = async (status: TrackingStatus) => {
    setBusy(true)
    setError(null)
    try {
      await api.updateEntry(entry.id, { status })
      setOpen(false)
      onChanged()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change that.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="status-menu" ref={container}>
      <button
        type="button"
        className="status-action"
        aria-haspopup="menu"
        aria-expanded={open}
        disabled={busy}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <span>{busy ? 'Saving…' : labels[entry.status]}</span>
        <ChevronIcon />
      </button>

      {open && (
        <ul className="status-options" role="menu">
          {order
            .filter((status) => status !== entry.status)
            .map((status) => (
              <li key={status} role="none">
                <button type="button" role="menuitem" onClick={() => void moveTo(status)}>
                  Set as {labels[status].toLowerCase()}
                </button>
              </li>
            ))}

          <li role="none">
            <button
              type="button"
              role="menuitem"
              className="menu-footer"
              onClick={() => {
                setOpen(false)
                onOpenEditor()
              }}
            >
              Open editor
            </button>
          </li>
        </ul>
      )}

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
