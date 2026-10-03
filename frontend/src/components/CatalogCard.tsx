import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import type { SearchResult, TrackingStatus } from '../api/client'
import { mediaPathFor } from '../modules/registry'
import { coverBlurClass, useBlurAdult } from '../content/blur'
import { AddToShelfMenu } from './AddToShelfMenu'
import { CardPeek } from './CardPeek'

/** Whether a hover has anything to say beyond the title and year the card already shows. */
const hasPeek = (result: SearchResult): boolean => {
  const facets = result.facets ?? {}
  return ['score', 'studio', 'format', 'genres', 'nextEpisode'].some((key) => facets[key] !== undefined)
}

interface Props {
  result: SearchResult
  state: 'idle' | 'saving' | 'tracked'
  onTrack: (status: TrackingStatus) => void
  onEdit: () => void
}

/**
 * One catalogue result, as search and browse both show it: a cover, a title, a year, and the
 * menu that puts it on a shelf.
 *
 * <p>Distinct from the library's own card, which shows progress and a rating for something
 * already tracked. This one is for a title you do not have yet.
 *
 * <p>The cover and the title open the title's page; the shelf menu sits over the cover rather
 * than inside the link, so the one place a click does something else is the one place it
 * looks like it would.
 */
export const CatalogCard = ({ result, state, onTrack, onEdit }: Props) => {
  const blur = useBlurAdult()
  const path = mediaPathFor(result)
  const [peek, setPeek] = useState<DOMRect | null>(null)

  // A card fixed to the window would be left behind by a scroll, so a scroll closes it.
  useEffect(() => {
    if (!peek) return
    const close = () => setPeek(null)
    window.addEventListener('scroll', close, { passive: true, capture: true })
    return () => window.removeEventListener('scroll', close, { capture: true })
  }, [peek])

  /** Only where a pointer hovers: a tap on a phone is a choice, not a look. */
  const look = (event: React.MouseEvent<HTMLDivElement>) => {
    if (hasPeek(result) && window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
      setPeek(event.currentTarget.getBoundingClientRect())
    }
  }

  return (
    <article className="card cover-card">
      <div className="cover-art" onMouseEnter={look} onMouseLeave={() => setPeek(null)}>
        <Link className="cover-link" to={path} aria-label={result.title}>
          {result.coverUrl ? (
            <img
              className={coverBlurClass(result.adult, blur)}
              src={result.coverUrl}
              alt=""
              loading="lazy"
            />
          ) : (
            <div className="cover-placeholder" aria-hidden="true" />
          )}
        </Link>

        <AddToShelfMenu result={result} state={state} onAdd={onTrack} onEdit={onEdit} />
      </div>

      {peek && <CardPeek result={result} anchor={peek} />}

      <div className="cover-body">
        <h2>
          <Link to={path}>{result.title}</Link>
        </h2>
        <p className="muted">{result.releaseDate?.slice(0, 4) ?? 'Unreleased'}</p>
      </div>
    </article>
  )
}
