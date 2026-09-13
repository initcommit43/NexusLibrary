import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { ApiError, type MediaType, type TrackingStatus } from '../api/client'
import { statusLabelsFor, typeDefinitionFor } from '../modules/registry'
import { STATUS_ORDER } from './trackingStatus'
import { useMenuDismiss } from './useMenuDismiss'

const DotsIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="currentColor" aria-hidden>
    <circle cx="5" cy="12" r="1.8" />
    <circle cx="12" cy="12" r="1.8" />
    <circle cx="19" cy="12" r="1.8" />
  </svg>
)

const CheckIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <path d="m5 12.5 4.5 4.5L19 7.5" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

/** The least room a menu leaves between itself and the window's edge. */
const EDGE = 8

/** Between the button and the menu hung from it. */
const OFFSET = 4

/**
 * The "…" in the corner of a phone's cover card, and the list of shelves behind it.
 *
 * <p>Pinned to the window rather than hung inside the card: the card sits in a row that
 * scrolls sideways, and a scrolling box clips whatever reaches out of it on either axis, so a
 * menu inside it would open as a sliver with a scrollbar. Pinned, it cannot follow the row, so
 * any scroll closes it rather than leaving it floating over a different cover.
 */
export const CoverStatusMenu = ({
  title,
  mediaType,
  current,
  onChoose,
}: {
  title: string
  mediaType: MediaType
  /** The shelf it is on, or null for a title not in the library yet. */
  current: TrackingStatus | null
  onChoose: (status: TrackingStatus) => Promise<void>
}) => {
  const { open, setOpen, dismiss, container, trigger } = useMenuDismiss<
    HTMLDivElement,
    HTMLButtonElement
  >()
  const menu = useRef<HTMLUListElement>(null)
  const [place, setPlace] = useState<{ top: number; left: number } | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const labels = statusLabelsFor(mediaType)
  const order = typeDefinitionFor(mediaType)?.statusOrder ?? STATUS_ORDER

  // Measured once drawn, so it can open upwards from a card low on the screen.
  useLayoutEffect(() => {
    const button = trigger.current?.getBoundingClientRect()
    const list = menu.current?.getBoundingClientRect()
    if (!open || !button || !list) return

    const left = Math.min(
      Math.max(button.right - list.width, EDGE),
      window.innerWidth - list.width - EDGE,
    )
    const below = button.bottom + OFFSET
    const top =
      below + list.height > window.innerHeight - EDGE
        ? Math.max(EDGE, button.top - OFFSET - list.height)
        : below
    setPlace({ top, left })
  }, [open, trigger, error])

  useEffect(() => {
    if (!open) return

    const close = () => setOpen(false)
    window.addEventListener('scroll', close, { capture: true, passive: true })
    window.addEventListener('resize', close)
    return () => {
      window.removeEventListener('scroll', close, { capture: true })
      window.removeEventListener('resize', close)
    }
  }, [open, setOpen])

  const choose = async (status: TrackingStatus) => {
    if (status === current) {
      dismiss()
      return
    }

    setBusy(true)
    setError(null)
    try {
      await onChoose(status)
      setOpen(false)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not change that.')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="cover-menu" ref={container}>
      <button
        type="button"
        ref={trigger}
        className="cover-menu-button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Status of ${title}`}
        disabled={busy}
        onClick={() => {
          setPlace(null)
          setError(null)
          setOpen((wasOpen) => !wasOpen)
        }}
      >
        <DotsIcon />
      </button>

      {open && (
        <ul
          ref={menu}
          className="group cover-menu-options"
          role="menu"
          aria-label={`Status of ${title}`}
          style={place ?? { visibility: 'hidden' }}
        >
          {order.map((status) => (
            <li key={status} role="none">
              <button
                type="button"
                role="menuitemradio"
                aria-checked={status === current}
                className="group-row"
                disabled={busy}
                onClick={() => void choose(status)}
              >
                <span className="group-row-body">
                  <span className="group-row-text">
                    <span className="group-row-title">{labels[status]}</span>
                  </span>
                  {status === current && (
                    <span className="group-row-trailing">
                      <CheckIcon />
                    </span>
                  )}
                </span>
              </button>
            </li>
          ))}

          {error && (
            <li className="cover-menu-error" role="alert">
              {error}
            </li>
          )}
        </ul>
      )}
    </div>
  )
}
