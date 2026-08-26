import { useEffect, useRef, useState, type ReactNode } from 'react'

interface Props {
  children: ReactNode
  /** Announced to a screen reader, since the arrows are otherwise two unlabelled buttons. */
  label: string
}

/**
 * A shelf you can step through, rather than six covers and a "view all".
 *
 * <p>The row is a real scroll container, so a trackpad, a touchscreen and the keyboard all
 * work without any of this; the arrows are an addition for a mouse, and they hide themselves
 * when there is nothing in that direction to reach.
 */
export const Carousel = ({ children, label }: Props) => {
  const row = useRef<HTMLDivElement>(null)
  const [atStart, setAtStart] = useState(true)
  const [atEnd, setAtEnd] = useState(true)

  const readPosition = () => {
    const element = row.current
    if (!element) return
    // A page of slack, because sub-pixel widths mean the end is rarely reached exactly.
    setAtStart(element.scrollLeft <= 1)
    setAtEnd(element.scrollLeft + element.clientWidth >= element.scrollWidth - 1)
  }

  useEffect(() => {
    readPosition()
    const element = row.current
    if (!element) return

    // Covers arriving, and the window changing shape, both move the far end.
    const observer = new ResizeObserver(readPosition)
    observer.observe(element)
    return () => observer.disconnect()
  }, [children])

  /** Just under a full row, so one card stays on screen as an anchor between steps. */
  const step = (direction: 1 | -1) => {
    const element = row.current
    if (!element) return
    element.scrollBy({ left: direction * element.clientWidth * 0.9, behavior: 'smooth' })
  }

  return (
    <div className="carousel">
      <button
        type="button"
        className="carousel-arrow start"
        aria-label={`Scroll ${label} back`}
        hidden={atStart}
        onClick={() => step(-1)}
      >
        ‹
      </button>

      <div className="browse-row" ref={row} onScroll={readPosition}>
        {children}
      </div>

      <button
        type="button"
        className="carousel-arrow end"
        aria-label={`Scroll ${label} forward`}
        hidden={atEnd}
        onClick={() => step(1)}
      >
        ›
      </button>
    </div>
  )
}
