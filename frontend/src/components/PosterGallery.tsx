import { Link } from 'react-router-dom'

/** One cover in a gallery: where it goes, and the line it wears across its foot. */
export interface Poster {
  key: string
  title: string
  coverUrl: string | null
  to: string
  /**
   * Lines across the cover's foot: one for progress, two for something airing — the episode
   * and the wait are different facts and reading them as one phrase makes both harder to find.
   */
  caption?: string[]
  /** Episodes aired and not yet watched, marked in the corner as the library marks them. */
  waiting?: number | null
}

/**
 * Covers, at the size a cover is worth showing.
 *
 * <p>The app's own language everywhere else — shelves, favourites, a title's page — and the
 * reason a media library looks like one rather than like a settings screen. A caption rides
 * the foot of the cover instead of sitting beside it, so the grid stays a grid.
 */
export const PosterGallery = ({
  posters,
  oneRow = false,
}: {
  posters: Poster[]
  /** Exactly five across, whatever the width — a shelf is a taste of a list, not the list. */
  oneRow?: boolean
}) => {
  if (posters.length === 0) return null

  return (
    <ul className={oneRow ? 'poster-gallery one-row' : 'poster-gallery'}>
      {posters.map((poster) => (
        <li key={poster.key} className="poster-slot">
          {/*
            * Only where something is waiting. On a shelf headed Airing, a mark saying "this
            * airs" repeats the heading; the number is the part the heading cannot say.
            */}
          {poster.waiting !== null && poster.waiting !== undefined && poster.waiting > 0 && (
            <span className="airing-mark" title={`${poster.waiting} waiting to watch`}>
              {poster.waiting}
            </span>
          )}
          <Link className="poster" to={poster.to} title={poster.title} aria-label={poster.title}>
            {poster.coverUrl ? (
              <img src={poster.coverUrl} alt="" loading="lazy" />
            ) : (
              <span className="poster-blank cover-placeholder" aria-hidden="true" />
            )}
            {poster.caption && poster.caption.length > 0 && (
              <span className="poster-caption">
                {poster.caption.map((line) => (
                  <span key={line}>{line}</span>
                ))}
              </span>
            )}
          </Link>
        </li>
      ))}
    </ul>
  )
}
