import { useEffect, useState } from 'react'

/**
 * Whether the page has been scrolled further than this many pixels, read once a frame.
 * Null switches it off: nothing is listened to and the answer is always no.
 */
export const useScrolledPast = (limit: number | null): boolean => {
  const [past, setPast] = useState(false)

  useEffect(() => {
    if (limit === null) return
    let ticking = false
    const update = () => {
      setPast(window.scrollY > limit)
      ticking = false
    }
    const onScroll = () => {
      if (ticking) return
      ticking = true
      requestAnimationFrame(update)
    }
    update()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [limit])

  return limit !== null && past
}
