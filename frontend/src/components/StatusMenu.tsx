import { useState } from 'react'
import { api, errorMessage, type TrackedItem, type TrackingStatus } from '../api/client'
import { statusLabelsFor, typeDefinitionFor } from '../modules/registry'
import { STATUS_ORDER } from './trackingStatus'
import { useMenuDismiss } from './useMenuDismiss'

const ChevronIcon = () => (
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 9 6 6 6-6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

const DotsIcon = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden>
    <circle cx="5" cy="12" r="1.8" />
    <circle cx="12" cy="12" r="1.8" />
    <circle cx="19" cy="12" r="1.8" />
  </svg>
)

/**
 * The status an entry is on, and a menu to move it somewhere else.
 *
 * <p>Moving something between lists is the commonest thing anyone does here, so it is one
 * click rather than a trip through the editor — which stays at the bottom of the menu for
 * everything a status cannot express. A phone's cover carries {@code CoverStatusMenu} instead.
 *
 * <p>{@code dots} swaps the labelled button for a bare "…", for the desktop list, where the
 * section heading already names the shelf and forty labels saying it again are noise.
 */
export const StatusMenu = ({
  entry,
  onChanged,
  onOpenEditor,
  dots = false,
}: {
  entry: TrackedItem
  onChanged: (updated: TrackedItem) => void
  onOpenEditor: () => void
  dots?: boolean
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
      setError(errorMessage(err, 'Could not change that.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="status-menu" ref={container}>
      {dots ? (
        <button
          type="button"
          className="status-dots"
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label={`Status of ${entry.title}`}
          disabled={busy}
          onClick={() => setOpen((wasOpen) => !wasOpen)}
        >
          <DotsIcon />
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

          {/* A "…" has no room beside it, so its failure is said inside the menu it came from. */}
          {dots && error && (
            <li className="status-options-error" role="alert">
              {error}
            </li>
          )}
        </ul>
      )}

      {!dots && error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}
    </div>
  )
}
