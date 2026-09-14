import type { Ref } from 'react'
import { Link } from 'react-router-dom'
import type { MediaType, SearchResult, TrackingStatus } from '../api/client'
import { mediaPathFor } from '../modules/registry'
import { CoverStatusMenu } from './CoverStatusMenu'
import { ChevronRight } from './GroupedList'
import { useShelfResults } from './useShelfResults'
import { coverBlurClass, useBlurAdult } from '../content/blur'

/** What a title needs to be put on a shelf, whether or not it is in the library yet. */
export type Shelvable = Pick<SearchResult, 'source' | 'externalId' | 'mediaType'>

/** One cover in a phone's shelf row. */
export interface RowCard extends Shelvable {
  key: string
  title: string
  coverUrl: string | null
  adult: boolean
  to: string
  /** One muted line under the title: a countdown, a count, a year. */
  subtitle: string | null
  status: TrackingStatus | null
  /** Episodes aired and not yet watched, marked in the corner as the library marks them. */
  waiting?: number | null
}

const CardCover = ({ card }: { card: RowCard }) => {
  const blur = useBlurAdult()
  return card.coverUrl ? (
    <img
      className={coverBlurClass(card.adult, blur)}
      src={card.coverUrl}
      alt=""
      loading="lazy"
    />
  ) : (
    <span className="cover-placeholder" />
  )
}

/**
 * A shelf as the native app draws Home: a title with its way through, then covers in a line
 * that scrolls sideways. The row bleeds to the window's edge, so a cover cut off there says
 * there is more of it.
 */
export const ShelfRow = ({
  title,
  to,
  cards,
  onStatus,
  frame,
}: {
  title: string
  /** Where the whole shelf lives; no "See all" where there is no such page. */
  to?: string
  cards: RowCard[]
  onStatus: (item: Shelvable, status: TrackingStatus) => Promise<void>
  frame?: Ref<HTMLElement>
}) => (
  <section className="shelf-row-section" ref={frame}>
    <div className="shelf-row-head">
      <h2>{title}</h2>
      {to && (
        <Link className="shelf-row-more" to={to}>
          See all
          <ChevronRight />
        </Link>
      )}
    </div>

    <ul className="chip-row shelf-row">
      {cards.map((card) => (
        <li key={card.key} className="shelf-card">
          <div className="shelf-card-cover">
            {/* The title under it is the same link and the one a screen reader is given. */}
            <Link to={card.to} tabIndex={-1} aria-hidden="true">
              <CardCover card={card} />
            </Link>
            {card.waiting !== null && card.waiting !== undefined && card.waiting > 0 && (
              <span className="airing-mark" title={`${card.waiting} waiting to watch`}>
                {card.waiting}
              </span>
            )}
            <CoverStatusMenu
              title={card.title}
              mediaType={card.mediaType}
              current={card.status}
              onChoose={(status) => onStatus(card, status)}
            />
          </div>
          <Link className="shelf-card-title" to={card.to}>
            {card.title}
          </Link>
          {card.subtitle && <span className="shelf-card-subtitle">{card.subtitle}</span>}
        </li>
      ))}
    </ul>
  </section>
)

/** A catalogue shelf as a phone's row, fetched as it nears the screen as the gallery is. */
export const CatalogueShelfRow = ({
  title,
  mediaType,
  shelf,
  to,
  statusOf,
  onStatus,
}: {
  title: string
  mediaType: MediaType
  shelf: string
  to: string
  /** Which shelf of the reader's own a result is already on, if any. */
  statusOf: (item: Shelvable) => TrackingStatus | null
  onStatus: (item: Shelvable, status: TrackingStatus) => Promise<void>
}) => {
  const { frame, results } = useShelfResults<HTMLElement>(mediaType, shelf)

  return (
    <ShelfRow
      frame={frame}
      title={title}
      to={to}
      onStatus={onStatus}
      cards={results.map((item) => ({
        key: `${item.source}-${item.externalId}`,
        source: item.source,
        externalId: item.externalId,
        mediaType: item.mediaType,
        title: item.title,
        coverUrl: item.coverUrl,
        adult: item.adult,
        to: mediaPathFor(item),
        // The same line a catalogue card carries on search and browse.
        subtitle: item.releaseDate?.slice(0, 4) ?? 'Unreleased',
        status: statusOf(item),
      }))}
    />
  )
}
