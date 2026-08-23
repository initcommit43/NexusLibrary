import { useEffect, useState } from 'react'

/** Below this the header always shows: hiding it at the very top reads as a glitch. */
const REVEAL_ABOVE = 96

/** Ignores the jitter of a trackpad or a rubber-band bounce at the edges. */
const MOVEMENT_THRESHOLD = 6

/**
 * Hides the header while reading down the page and brings it back the moment you scroll up.
 *
 * <p>Direction rather than position: on a long shelf the header is in the way going down and
 * wanted the instant you turn around, which is not something a scroll offset can tell you.
 */
export const useHideOnScroll = (): boolean => {
  const [hidden, setHidden] = useState(false)

  useEffect(() => {
    let lastY = window.scrollY
    let ticking = false

    const update = () => {
      const y = window.scrollY
      const movement = y - lastY

      if (Math.abs(movement) > MOVEMENT_THRESHOLD) {
        setHidden(movement > 0 && y > REVEAL_ABOVE)
        lastY = y
      } else if (y <= REVEAL_ABOVE) {
        setHidden(false)
      }
      ticking = false
    }

    const onScroll = () => {
      // Scroll fires far faster than the screen redraws; one read per frame is enough.
      if (ticking) return
      ticking = true
      requestAnimationFrame(update)
    }

    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  return hidden
}
