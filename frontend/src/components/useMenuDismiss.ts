import { useEffect, useRef, useState } from 'react'

/**
 * Open state for a menu that closes on Escape or on a pointer going down anywhere outside it.
 *
 * <p>The listeners sit on the document because what closes a menu is precisely the clicks
 * the menu never receives, and they are bound only while it is open — a shelf of forty
 * cards otherwise means forty idle document listeners.
 *
 * <p>Escape hands focus back to whatever {@code trigger} is attached to, so a keyboard is left
 * where it opened the menu from rather than on the page body; a pointer outside is a reader
 * who has already chosen where to be next, so it moves nothing. A menu that attaches no
 * trigger closes on Escape and leaves focus alone.
 */
export const useMenuDismiss = <C extends HTMLElement, T extends HTMLElement = HTMLElement>() => {
  const [open, setOpen] = useState(false)
  const container = useRef<C>(null)
  const trigger = useRef<T>(null)

  /*
   * Focus moves before the menu closes, not after: a menu that opens on focus would take the
   * trigger's focus as a reason to open again, and closing last is what has the final word.
   * It touches only refs and a state setter, so the copy the listener below holds never goes
   * stale.
   */
  const dismiss = () => {
    trigger.current?.focus({ preventScroll: true })
    setOpen(false)
  }

  useEffect(() => {
    if (!open) return

    const onPointerDown = (event: PointerEvent) => {
      if (!container.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') dismiss()
    }

    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return { open, setOpen, dismiss, container, trigger }
}
