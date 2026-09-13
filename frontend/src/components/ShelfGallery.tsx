import { Link } from 'react-router-dom'
import type { MediaType } from '../api/client'
import { PosterGallery } from './PosterGallery'
import { mediaPathFor } from '../modules/registry'
import { useShelfResults } from './useShelfResults'

/** One catalogue shelf as a gallery, fetched when it is nearly on screen. */
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
  const { frame, results } = useShelfResults<HTMLElement>(mediaType, shelf)

  return (
    <section className="status-section" ref={frame}>
      <h2>
        {title}
        <Link className="section-action" to={`/browse/${moduleSlug}/${typeSlug}/${shelf}`}>
          See all →
        </Link>
      </h2>
      <PosterGallery
        posters={results.map((item) => ({
          key: `${item.source}-${item.externalId}`,
          title: item.title,
          coverUrl: item.coverUrl,
          to: mediaPathFor(item),
        }))}
        oneRow
      />
    </section>
  )
}
