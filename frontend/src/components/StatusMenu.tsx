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

const MoreIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden>
    <circle cx="5.5" cy="12" r="1.8" />
    <circle cx="12" cy="12" r="1.8" />
    <circle cx="18.5" cy="12" r="1.8" />
  </svg>
)

/**
 * The status an entry is on, and a menu to move it somewhere else.
 *
 * <p>Moving something between lists is the commonest thing anyone does here, so it is one
 * click rather than a trip through the editor — which stays at the bottom of the menu for
 * everything a status cannot express.
 *
 * <p>{@code compact} trades the labelled button for a round "…" that can sit on a cover's
 * corner, where a status name has no room. The menu behind it is the same.
 */
export const StatusMenu = ({
  entry,
  onChanged,
  onOpenEditor,
  compact = false,
}: {
  entry: TrackedItem
  onChanged: (updated: TrackedItem) => void
  onOpenEditor: () => void
  compact?: boolean
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
      const updated = await api.updateEntry(entry.id, { status })
      setOpen(false)
      onChanged(updated)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change that.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={compact ? 'status-menu is-compact' : 'status-menu'} ref={container}>
      {compact ? (
        <button
          type="button"
          className="icon-button status-more"
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label={`${labels[entry.status]}: change the status of ${entry.title}`}
          disabled={busy}
          onClick={() => setOpen((wasOpen) => !wasOpen)}
        >
          <MoreIcon />
        </button>
      ) : (
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
      )}

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
