import { useEffect, useRef, useState, type ReactNode } from 'react'

interface Props {
  children: ReactNode
  /** Announced to a screen reader, since the arrows are otherwise two unlabelled buttons. */
  label: string
}

/**
 * How far from an edge still counts as being at it.
 *
 * <p>Wider than a pixel because nothing here lands on one: the columns are a calc of a
 * percentage, the row carries padding, and proximity snapping settles wherever it likes. A
 * pixel of slack left the forward arrow showing at a shelf's end, pointing at nothing.
 */
const SLACK = 4

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

    const left = element.scrollLeft
    setAtStart(left <= SLACK)
    setAtEnd(left + element.clientWidth >= element.scrollWidth - SLACK)
  }

  useEffect(() => {
    readPosition()
    const element = row.current
    if (!element) return

    // Covers arriving, and the window changing shape, both move the far end. The observer
    // watches the row's own box; the children are a dependency because a shelf filling in
    // changes how far it scrolls without changing that box at all.
    const observer = new ResizeObserver(readPosition)
    observer.observe(element)

    // A smooth step ends after the last scroll event, and snapping can settle a pixel or two
    // past where it landed — without this the far end is read from midway through the move.
    element.addEventListener('scrollend', readPosition)
    return () => {
      observer.disconnect()
      element.removeEventListener('scrollend', readPosition)
    }
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
