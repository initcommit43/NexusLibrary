import { Link } from 'react-router-dom'
import type { MediaDetail } from '../api/client'
import { browsePathFor } from '../modules/registry'
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

/** "142 mins", from whichever of the two places the source recorded a running time. */
const minutes = (value: unknown): string | null => {
  const runtime = text(value)
  return runtime ? `${runtime} mins` : null
}

/**
 * TMDB reports money in whole US dollars whatever the film's own currency was, so the figure
 * is shown as what it is rather than converted into a guess.
 */
const money = (value: unknown): string | null =>
  typeof value === 'number' && value > 0
    ? value.toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 })
    : null

const date = (iso: string | null) =>
  iso ? new Date(iso).toLocaleDateString(undefined, { day: 'numeric', month: 'short', year: 'numeric' }) : null

/**
 * The companies behind a title, split the way the source splits them: the studio that made
 * it and the producers who paid for it are different credits, and conflating them is wrong.
 */
const companies = (
  detail: Record<string, unknown>,
  main: boolean,
  source: string,
): Fact[] => {
  const edges = record(detail.studios).edges
  if (!Array.isArray(edges)) return []

  return edges.flatMap((raw) => {
    const edge = record(raw)
    if ((edge.isMain === true) !== main) return []

    const node = record(edge.node)
    const name = text(node.name)
    const id = text(node.id)
    if (!name) return []

    // Their own page where the source gave them an id, which is what makes "what else did
    // they make" a question this app can answer rather than one to go and ask elsewhere.
    return [{ label: name, to: id ? `/studio/${source}/${id}` : undefined }]
  })
}

/** One value in the column, and where it leads if it leads anywhere. */
type Fact = { label: string; to?: string }

/**
 * The tags AniList files a title under, its own order — most agreed-with first.
 *
 * <p>Spoiler tags are left out. They are how a tag list tells you the twist before you have
 * watched the thing, and a sidebar is read at a glance rather than opened deliberately.
 */
const tagsOf = (detail: Record<string, unknown>): string[] => {
  const tags = detail.tags
  if (!Array.isArray(tags)) return []

  return tags.flatMap((raw) => {
    const tag = record(raw)
    if (tag.isMediaSpoiler === true) return []
    const name = text(tag.name)
    return name ? [name] : []
  })
}

/**
 * Which media types have a browse filter whose values are the words shown here.
 *
 * <p>AniList files by name, so a genre on this page is the genre a filter takes. TMDB and
 * IGDB file by id and show a name, so the same link would ask for a genre called "Action"
 * where the filter wanted 28 — those stay as plain text until their filters take names.
 */
const FILTERS_BY_NAME = new Set(['ANIME', 'MANGA'])

/**
 * The column of facts beside a title. Rows appear only when the source actually knows the
 * answer, so a manga is not padded out with empty episode counts.
 */
/** Media that arrive in parts, and so have a run that can still be going on. */
const SERIAL = new Set(['ANIME', 'SHOW', 'MANGA'])

const releaseState = (mediaType: string, itemState: string | null): string => {
  const serial = SERIAL.has(mediaType)

  if (itemState === 'UPCOMING') return serial ? 'Not yet aired' : 'Unreleased'
  if (itemState === 'ONGOING') return serial ? 'Airing' : 'In development'
  return serial ? 'Finished' : 'Released'
}

/**
 * The year it came out, which is what this row is read for — the state it is in only
 * answers the question while there is no year to give.
 */
const released = (media: MediaDetail): string => {
  const year = media.releaseDate ? String(new Date(media.releaseDate).getFullYear()) : null
  return year && media.itemState !== 'UPCOMING'
    ? year
    : releaseState(media.mediaType, media.itemState)
}

export const MediaFacts = ({ media }: { media: MediaDetail }) => {
  const meta = media.metadata
  const detail = (meta.detail ?? {}) as Record<string, unknown>
  const titles = record(detail.title)

  const rows: [string, string | null][] = [
    ['Format', text(meta.format)],
    ['Episodes', text(meta.episodes)],
    ['Chapters', text(meta.chapters)],
    ['Volumes', text(meta.volumes)],
    ['Episode duration', minutes(detail.duration)],
    ['Runtime', minutes(meta.runtimeMinutes ?? detail.runtime)],
    ['Seasons', text(meta.seasons)],
    ['Pages', text(meta.pageCount)],
    ['First published', text(detail.firstPublished)],
    /*
     * The release, not the reader's progress. A bare "Finished" on a game reads as a game
     * you have finished, which is the one thing on this page that is not about you — and a
     * title that is out is better described by the year it came out than by being out.
     *
     * Serial media are still described as finishing, because that is what an anime or a
     * comic does; a game or a film is released once and then simply is.
     */
    ['Release', released(media)],
    ['Start date', date(media.releaseDate)],
    ['Season', season(detail)],
    ['Average score', text(meta.externalRating) ? `${text(meta.externalRating)}%` : null],
    ['Popularity', text(detail.popularity)],
    ['Favourites', text(detail.favourites)],
    ['Source', text(detail.source)],
    ['Hashtag', text(detail.hashtag)],
    ['Budget', money(detail.budget)],
    ['Box office', money(detail.revenue)],
  ]

  // Studios come from the detail when it is loaded, since only there are they split by role.
  const studios = companies(detail, true, media.source)
  const producers = companies(detail, false, media.source)
  /** Which values lead into the browse filter, where the filter takes the words shown here. */
  const narrows = FILTERS_BY_NAME.has(media.mediaType)
  const plain = (values: string[]): Fact[] => values.map((label) => ({ label }))

  /** Both lead into the one box that holds them, marked with the side they came from. */
  const narrowing = (values: string[], mark: string): Fact[] =>
    values.map((label) => ({
      label,
      to: narrows ? browsePathFor(media.mediaType, 'genres', mark + label) : undefined,
    }))

  const stacked: [string, Fact[]][] = [
    ['Studios', studios.length > 0 ? studios : plain(list(meta.studios))],
    ['Producers', producers],
    ['Authors', plain(list(meta.authors))],
    ['Networks', plain(list(detail.networks))],
    ['Platforms', plain(list(meta.platforms))],
    ['Genres', narrowing(list(meta.genres), 'genre:')],
    ['Tags', narrowing(tagsOf(detail), 'tag:')],
    ['Romaji', plain([text(titles.romaji) ?? ''].filter(Boolean))],
    ['English', plain([text(titles.english) ?? ''].filter(Boolean))],
    ['Native', plain([text(titles.native) ?? ''].filter(Boolean))],
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
            {values.map((value) =>
              value.to ? (
                // A genre, a tag, a studio: each of them is the same question — what else is
                // like this, what else did they make — asked where it is read rather than
                // remembered and typed in somewhere else.
                <Link key={value.label} className="fact-value fact-link" to={value.to}>
                  {value.label}
                </Link>
              ) : (
                <span key={value.label} className="fact-value">
                  {value.label}
                </span>
              ),
            )}
          </div>
        ))}
    </aside>
  )
}
