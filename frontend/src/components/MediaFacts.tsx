import type { MediaDetail } from '../api/client'
import { countdown, readNextEpisode, readRankings } from './mediaDetail'

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() ? value : typeof value === 'number' ? String(value) : null

const list = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []

const record = (value: unknown): Record<string, unknown> =>
  typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}

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
 * The companies behind a title, split the way the source splits them: the studio that made
 * it and the producers who paid for it are different credits, and conflating them is wrong.
 */
const companies = (detail: Record<string, unknown>, main: boolean): string[] => {
  const edges = record(detail.studios).edges
  if (!Array.isArray(edges)) return []

  return edges.flatMap((raw) => {
    const edge = record(raw)
    if ((edge.isMain === true) !== main) return []
    const name = text(record(edge.node).name)
    return name ? [name] : []
  })
}

/**
 * The column of facts beside a title. Rows appear only when the source actually knows the
 * answer, so a manga is not padded out with empty episode counts.
 */
export const MediaFacts = ({ media }: { media: MediaDetail }) => {
  const meta = media.metadata
  const detail = (meta.detail ?? {}) as Record<string, unknown>
  const titles = record(detail.title)

  const rows: [string, string | null][] = [
    ['Format', text(meta.format)],
    ['Episodes', text(meta.episodes)],
    ['Chapters', text(meta.chapters)],
    ['Volumes', text(meta.volumes)],
    ['Episode duration', text(detail.duration) ? `${text(detail.duration)} mins` : null],
    [
      'Status',
      media.itemState === 'ONGOING'
        ? 'Releasing'
        : media.itemState === 'UPCOMING'
          ? 'Not yet released'
          : 'Finished',
    ],
    ['Start date', date(media.releaseDate)],
    ['Season', season(detail)],
    ['Average score', text(meta.externalRating) ? `${text(meta.externalRating)}%` : null],
    ['Mean score', text(detail.meanScore) ? `${text(detail.meanScore)}%` : null],
    ['Popularity', text(detail.popularity)],
    ['Favourites', text(detail.favourites)],
    ['Source', text(detail.source)],
    ['Hashtag', text(detail.hashtag)],
  ]

  // Studios come from the detail when it is loaded, since only there are they split by role.
  const studios = companies(detail, true)
  const producers = companies(detail, false)
  const stacked: [string, string[]][] = [
    ['Studios', studios.length > 0 ? studios : list(meta.studios)],
    ['Producers', producers],
    ['Platforms', list(meta.platforms)],
    ['Genres', list(meta.genres)],
    ['Romaji', [text(titles.romaji) ?? ''].filter(Boolean)],
    ['English', [text(titles.english) ?? ''].filter(Boolean)],
    ['Native', [text(titles.native) ?? ''].filter(Boolean)],
  ]

  const rankings = readRankings(detail)

  // The one fact that changes by the hour, so it leads rather than sitting in the list.
  const next = readNextEpisode(detail)
  const until = next ? countdown(next.airingAt) : null

  return (
    <aside className="media-facts">
      {rankings.map((ranking) => (
        <span key={ranking} className="ranking">
          {ranking}
        </span>
      ))}

      {next && until && (
        <div className="fact airing">
          <span className="fact-label">Airing</span>
          <span className="fact-value">
            Episode {next.episode} in {until}
          </span>
        </div>
      )}

      {rows
        .filter(([, value]) => value)
        .map(([label, value]) => (
          <div key={label} className="fact">
            <span className="fact-label">{label}</span>
            <span className="fact-value">{value}</span>
          </div>
        ))}

      {stacked
        .filter(([, values]) => values.length > 0)
        .map(([label, values]) => (
          <div key={label} className="fact">
            <span className="fact-label">{label}</span>
            {/* One per line: a run of comma-separated genres is unreadable in a narrow column. */}
            {values.map((value) => (
              <span key={value} className="fact-value">
                {value}
              </span>
            ))}
          </div>
        ))}
    </aside>
  )
}
