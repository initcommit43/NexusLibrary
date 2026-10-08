import { useEffect, useRef, useState } from 'react'
import { api, type MediaType, type SearchResult } from '../api/client'

/** How far ahead of the viewport a shelf starts loading, so it is filled by the time it lands. */
const REACH = '400px'

/*
 * One row of it. A catalogue shelf has no end, so the question it answers here is "anything
 * worth a look" rather than "what is there" — and the answer to the second is a click away.
 */
const SHOWN = 5

/**
 * The start of one catalogue shelf, fetched when the element holding it is nearly on screen.
 * Shared by Home's gallery and the phone's scrolling row, which draw the same answer.
 *
 * <p>The answers are the same for every reader and served from the server's memory, so this
 * costs no external budget — but a page that fires four requests before drawing its own first
 * line still feels slower than one that does not.
 */
export const useShelfResults = <T extends HTMLElement>(mediaType: MediaType, shelf: string) => {
  /** Null until the shelf has been asked and has answered, so it can hold its shape meanwhile. */
  const [results, setResults] = useState<SearchResult[] | null>(null)
  const frame = useRef<T>(null)

  useEffect(() => {
    const node = frame.current
    if (!node) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return
        observer.disconnect()

        api
          .browse(mediaType, shelf)
          .then((answer) => setResults(answer.items.slice(0, SHOWN)))
          // A shelf that will not load is not worth an alarm on a page about your own
          // library: the section keeps its heading and stays quiet.
          .catch(() => setResults([]))
      },
      { rootMargin: `${REACH} 0px` },
    )

    observer.observe(node)
    return () => observer.disconnect()
  }, [mediaType, shelf])

  return { frame, results }
}
