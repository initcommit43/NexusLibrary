import { useRef, useState } from 'react'
import { errorMessage, type Framing } from '../api/client'
import { type DragOrigin, draggedTo, hidden } from './framing'
import { useEscapeKey } from './useEscapeKey'

/**
 * Holding a picture where the reader put it, for the avatar circle and the banner strip.
 *
 * <p>The two frames are different shapes with the same problem, so they had the same eighty
 * lines each: an edit that resets when the picture changes or the bar is left, a drag measured
 * against how much of the image is actually hidden, and a save that keeps the picture on screen
 * whichever way it went. The arithmetic already lived in {@link framing}; this is the state
 * around it.
 *
 * <p>The edit is keyed by a session string rather than reset in an effect, so leaving adjust
 * mode is one render rather than a render and a correction. A different picture, or an
 * adjustment left rather than saved, both change the session — which is what makes closing the
 * bar a cancel.
 */
export const useFramingEditor = <T>({
  session,
  stored,
  adjusting,
  save,
  onFramed,
  onClose,
}: {
  /** What this edit belongs to. Change it and the framing goes back to what is stored. */
  session: string
  /** The framing to start from, read afresh whenever the session changes. */
  stored: () => Framing
  adjusting: boolean
  save: (framing: Framing) => Promise<T>
  onFramed: (framed: T) => void
  onClose: () => void
}) => {
  const [edit, setEdit] = useState(() => ({ session, framing: stored(), error: null as string | null }))
  const held = edit.session === session ? edit : { session, framing: stored(), error: null }

  const [busy, setBusy] = useState(false)

  const image = useRef<HTMLImageElement>(null)
  const drag = useRef<DragOrigin | null>(null)

  useEscapeKey(onClose, adjusting)

  const setFraming = (next: Framing) => setEdit({ ...held, framing: next })

  const startDrag = (event: React.PointerEvent<HTMLImageElement>) => {
    if (!adjusting) return
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { x: event.clientX, y: event.clientY, from: held.framing }
  }

  const moveDrag = (event: React.PointerEvent<HTMLImageElement>) => {
    const from = drag.current
    const room = hidden(image.current, held.framing.zoom)
    if (!from || !room) return

    setFraming(draggedTo(from, room, event.clientX, event.clientY))
  }

  const endDrag = () => {
    drag.current = null
  }

  const commit = async () => {
    setBusy(true)
    setEdit({ ...held, error: null })
    try {
      onFramed(
        await save({
          focusX: Math.round(held.framing.focusX),
          focusY: Math.round(held.framing.focusY),
          zoom: Math.round(held.framing.zoom),
        }),
      )
      onClose()
    } catch (err) {
      setEdit({ ...held, error: errorMessage(err, 'Could not save that.') })
    } finally {
      // Cleared on the way out however it went: the picture stays on the page after a save,
      // so a flag left set here is a Save button that never comes back.
      setBusy(false)
    }
  }

  return {
    framing: held.framing,
    error: held.error,
    busy,
    image,
    setFraming,
    /** Spread onto the img: the whole of the drag, in the four events it takes. */
    dragHandlers: {
      onPointerDown: startDrag,
      onPointerMove: moveDrag,
      onPointerUp: endDrag,
      onPointerCancel: endDrag,
    },
    commit,
  }
}
