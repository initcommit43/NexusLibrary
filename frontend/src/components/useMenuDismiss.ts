import { useEffect, useRef, useState } from 'react'

/**
 * Open state for a menu that closes on Escape or on a click anywhere outside it.
 *
 * <p>The listeners sit on the document because what closes a menu is precisely the clicks
 * the menu never receives, and they are bound only while it is open — a shelf of forty
 * cards otherwise means forty idle document listeners.
 */
export const useMenuDismiss = <T extends HTMLElement>() => {
  const [open, setOpen] = useState(false)
  const container = useRef<T>(null)

  useEffect(() => {
    if (!open) return

    const onPointerDown = (event: PointerEvent) => {
      if (!container.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }

    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

  return { open, setOpen, container }
}
