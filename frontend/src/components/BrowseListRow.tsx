import { Link } from 'react-router-dom'
import type { FilterField, SearchResult } from '../api/client'
import { coverBlurClass, useBlurAdult } from '../content/blur'
import { mediaPathFor } from '../modules/registry'

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() !== '' ? value : null

const count = (value: unknown): number | null => (typeof value === 'number' ? value : null)

const counted = (amount: number, one: string, many: string) =>
  `${amount.toLocaleString()} ${amount === 1 ? one : many}`

/**
 * A source's own word for a value, as its filter bar shows it: the facets arrive as the
 * source sends them ({@code TV}, {@code FINISHED}), and the bar already knows what to call them.
 */
const named = (fields: FilterField[], id: string, value: string | null): string | null =>
  value === null
    ? null
    : (fields.find((field) => field.id === id)?.options.find((option) => option.value === value)?.label ??
      value)

/** Two lines, the first standing out: how each column of the row says its fact. */
const Fact = ({ lead, under }: { lead: string | null; under: string | null }) => (
  <div className="list-row-fact">
    {lead && <span className="list-row-lead">{lead}</span>}
    {under && <span className="list-row-under">{under}</span>}
  </div>
)

/**
 * One result as a row: a small cover and the title with its genres, then the facts a list is
 * read for — how well it scores and with how many, what it is and how long, when it ran and
 * whether it still does. A column a source cannot fill is left blank rather than closed up,
 * so the rows stay aligned down the page.
 *
 * <p>A ranked shelf uses the same row with its place in front, so a top-100 chart and the
 * list view read as the same kind of thing.
 */
export const BrowseListRow = ({
  result,
  fields,
  rank,
}: {
  result: SearchResult
  fields: FilterField[]
  rank?: number
}) => {
  const blur = useBlurAdult()
  const facets = result.facets ?? {}
  const genres = Array.isArray(facets.genres) ? facets.genres.map(String) : []

  const score = count(facets.score)
  const audience = count(facets.popularity) ?? count(facets.votes)
  const audienceWord = count(facets.popularity) !== null ? 'users' : 'votes'

  const format = named(fields, 'format', text(facets.format))
  const length = count(facets.episodes) ?? count(facets.chapters)
  const lengthWords: [string, string] =
    count(facets.episodes) !== null ? ['episode', 'episodes'] : ['chapter', 'chapters']

  const season = named(fields, 'season', text(facets.season))
  const year = count(facets.seasonYear) ?? (result.releaseDate ? Number(result.releaseDate.slice(0, 4)) : null)
  const status = named(fields, 'status', text(facets.status))

  return (
    <Link className={rank === undefined ? 'card list-row' : 'card list-row has-rank'} to={mediaPathFor(result)}>
      {rank !== undefined && (
        <span className="list-row-rank" aria-label={`Number ${rank}`}>
          #{rank}
        </span>
      )}

      {result.coverUrl ? (
        <img className={coverBlurClass(result.adult, blur, 'list-row-cover')} src={result.coverUrl} alt="" loading="lazy" />
      ) : (
        <span className="list-row-cover cover-placeholder" aria-hidden="true" />
      )}

      <div className="list-row-main">
        <h3>{result.title}</h3>
        {genres.length > 0 && (
          <ul className="list-row-genres">
            {genres.map((genre) => (
              <li key={genre}>{genre.toLowerCase()}</li>
            ))}
          </ul>
        )}
      </div>

      <Fact
        lead={score !== null ? `${score}%` : null}
        under={audience !== null ? `${audience.toLocaleString()} ${audienceWord}` : null}
      />
      <Fact lead={format} under={length !== null ? counted(length, ...lengthWords) : null} />
      <Fact lead={[season, year].filter(Boolean).join(' ') || null} under={status} />
    </Link>
  )
}
