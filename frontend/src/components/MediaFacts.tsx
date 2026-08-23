import type { MediaDetail } from '../api/client'
import { readRankings } from './mediaDetail'

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() ? value : typeof value === 'number' ? String(value) : null

const list = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []

/** "Spring 2017" from AniList's separate season and year. */
const season = (detail: Record<string, unknown>): string | null => {
  const name = text(detail.season)
  const year = text(detail.seasonYear)
  if (!name) return year
  return `${name.charAt(0)}${name.slice(1).toLowerCase()}${year ? ` ${year}` : ''}`
}

const date = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : null

/**
 * The column of facts beside a title. Rows appear only when the source actually knows the
 * answer, so a manga is not padded out with empty episode counts.
 */
export const MediaFacts = ({ media }: { media: MediaDetail }) => {
  const meta = media.metadata
  const detail = (meta.detail ?? {}) as Record<string, unknown>

  const rows: [string, string | null][] = [
    ['Format', text(meta.format)],
    ['Episodes', text(meta.episodes)],
    ['Chapters', text(meta.chapters)],
    ['Volumes', text(meta.volumes)],
    ['Episode duration', text(detail.duration) ? `${text(detail.duration)} mins` : null],
    ['Status', media.itemState === 'ONGOING' ? 'Releasing' : media.itemState === 'UPCOMING' ? 'Not yet released' : 'Finished'],
    ['Start date', date(media.releaseDate)],
    ['Season', season(detail)],
    ['Average score', text(meta.externalRating) ? `${text(meta.externalRating)}%` : null],
    ['Mean score', text(detail.meanScore) ? `${text(detail.meanScore)}%` : null],
    ['Popularity', text(detail.popularity)],
    ['Favourites', text(detail.favourites)],
    ['Source', text(detail.source)],
    ['Hashtag', text(detail.hashtag)],
  ]

  const studios = list(meta.studios)
  const genres = list(meta.genres)
  const platforms = list(meta.platforms)

  const rankings = readRankings(detail)

  return (
    <aside className="media-facts">
      {rankings.map((ranking) => (
        <span key={ranking} className="ranking">
          {ranking}
        </span>
      ))}

      {rows
        .filter(([, value]) => value)
        .map(([label, value]) => (
          <div key={label} className="fact">
            <span className="fact-label">{label}</span>
            <span className="fact-value">{value}</span>
          </div>
        ))}

      {studios.length > 0 && (
        <div className="fact">
          <span className="fact-label">Studios</span>
          <span className="fact-value">{studios.join(', ')}</span>
        </div>
      )}

      {platforms.length > 0 && (
        <div className="fact">
          <span className="fact-label">Platforms</span>
          <span className="fact-value">{platforms.join(', ')}</span>
        </div>
      )}

      {genres.length > 0 && (
        <div className="fact">
          <span className="fact-label">Genres</span>
          <span className="fact-value">{genres.join(', ')}</span>
        </div>
      )}
    </aside>
  )
}
