import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, type MediaType } from '../api/client'
import { PosterGallery, type Poster } from './PosterGallery'
import { mediaPathFor } from '../modules/registry'

/** How far ahead of the viewport a shelf starts loading, so it is filled by the time it lands. */
const REACH = '400px'

/*
 * One row of it. A catalogue shelf has no end, so the question it answers here is "anything
 * worth a look" rather than "what is there" — and the answer to the second is a click away.
 */
const SHOWN = 5

/**
 * One catalogue shelf as a gallery, fetched when it is nearly on screen.
 *
 * <p>The answers are the same for every reader and served from the server's memory, so this
 * costs no external budget — but a page that fires four requests before drawing its own first
 * line still feels slower than one that does not.
 */
export const ShelfGallery = ({
  title,
  mediaType,
  shelf,
  moduleSlug,
  typeSlug,
}: {
  title: string
  mediaType: MediaType
  shelf: string
  moduleSlug: string
  typeSlug: string
}) => {
  const [posters, setPosters] = useState<Poster[] | null>(null)
  const frame = useRef<HTMLElement>(null)

  useEffect(() => {
    const node = frame.current
    if (!node) return

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) return
        observer.disconnect()

        api
          .browse(mediaType, shelf)
          .then((results) =>
            setPosters(
              results.items.slice(0, SHOWN).map((item) => ({
                key: `${item.source}-${item.externalId}`,
                title: item.title,
                coverUrl: item.coverUrl,
                to: mediaPathFor(item),
              })),
            ),
          )
          // A shelf that will not load is not worth an alarm on a page about your own
          // library: the section keeps its heading and stays quiet.
          .catch(() => setPosters([]))
      },
      { rootMargin: `${REACH} 0px` },
    )

    observer.observe(node)
    return () => observer.disconnect()
  }, [mediaType, shelf])

  return (
    <section className="status-section" ref={frame}>
      <h2>
        {title}
        <Link className="section-action" to={`/browse/${moduleSlug}/${typeSlug}/${shelf}`}>
          See all →
        </Link>
      </h2>
      <PosterGallery posters={posters ?? []} oneRow />
    </section>
  )
}
