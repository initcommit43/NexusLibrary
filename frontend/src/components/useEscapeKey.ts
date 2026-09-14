import { useEffect, useRef } from 'react'

/**
 * Escape closes whatever is on top.
 *
 * <p>Every dialog, picker and adjust bar wanted the same four lines — a keydown listener on
 * the document, put up on open and taken down again — and each copy was another chance to
 * forget the cleanup. Pass {@code false} for {@code active} where a control is only sometimes
 * dismissible (an avatar listens while it is being adjusted and not otherwise), so a page full
 * of closed dialogs is not a page full of idle document listeners.
 *
 * <p>The handler is held in a ref rather than depended on, so a caller can pass an inline
 * arrow without re-binding the listener on every render.
 */
export const useEscapeKey = (dismiss: () => void, active = true) => {
  const latest = useRef(dismiss)
  useEffect(() => {
    latest.current = dismiss
  })

  useEffect(() => {
    if (!active) return

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') latest.current()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [active])
}
