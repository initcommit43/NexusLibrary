import { createPortal } from 'react-dom'
import type { SearchResult } from '../api/client'
import { formatWord } from './listColumns'

const text = (value: unknown): string | null =>
  typeof value === 'string' && value.trim() !== '' ? value : null

const count = (value: unknown): number | null => (typeof value === 'number' ? value : null)

const seasonWord = (season: string) => season.charAt(0) + season.slice(1).toLowerCase()

/**
 * "6 days", "17 hours", "40 minutes": the largest unit only, as AniList puts it on a cover.
 * The page's own countdown is finer; at a glance the day is all anyone reads.
 */
const airingIn = (airingAt: number): string | null => {
  const seconds = airingAt - Math.floor(Date.now() / 1000)
  if (seconds <= 0) return null
  const [amount, unit] =
    seconds >= 86400
      ? [Math.floor(seconds / 86400), 'day']
      : seconds >= 3600
        ? [Math.floor(seconds / 3600), 'hour']
        : [Math.max(1, Math.floor(seconds / 60)), 'minute']
  return `${amount} ${unit}${amount === 1 ? '' : 's'}`
}

/** A face for the score, as AniList draws one: pleased, unsure or not, by how high it is. */
const ScoreFace = ({ score }: { score: number }) => {
  const mood = score >= 75 ? 'is-high' : score >= 60 ? 'is-mid' : 'is-low'
  const mouth = score >= 75 ? 'M8 14.5q4 3 8 0' : score >= 60 ? 'M8.5 15h7' : 'M8 16q4-3 8 0'
  return (
    <svg className={`peek-face ${mood}`} viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" aria-hidden>
      <circle cx="12" cy="12" r="9" strokeWidth="1.8" />
      <circle cx="9" cy="10" r="1" fill="currentColor" stroke="none" />
      <circle cx="15" cy="10" r="1" fill="currentColor" stroke="none" />
      <path d={mouth} strokeWidth="1.8" strokeLinecap="round" />
    </svg>
  )
}

/** How wide the card is, which decides whether it fits on the cover's right. */
const PEEK_WIDTH = 288
const GAP = 12

/**
 * The card that opens beside a cover while it is pointed at: when the next episode lands or
 * when it ran, how it scores, who made it, what it is and how long, and its genres — what
 * AniList answers before a title is opened.
 *
 * <p>Fixed to the window rather than set inside the card: shelves scroll sideways and clip
 * whatever leaves them, and the card has to sit outside the cover to be read beside it. It
 * opens to the cover's right, or its left where the window ends first. It takes no pointer
 * itself, so moving off the cover always closes it.
 */
export const CardPeek = ({ result, anchor }: { result: SearchResult; anchor: DOMRect }) => {
  const facets = result.facets ?? {}
  const score = count(facets.score)
  const studio = text(facets.studio)
  const format = text(facets.format)
  const episodes = count(facets.episodes) ?? count(facets.chapters)
  const unit = count(facets.episodes) !== null ? 'episode' : 'chapter'
  const genres = Array.isArray(facets.genres) ? facets.genres.map(String).slice(0, 3) : []

  const nextEpisode = count(facets.nextEpisode)
  const nextAt = count(facets.nextAiringAt)
  const until = nextAt !== null ? airingIn(nextAt) : null
  const season = text(facets.season)
  const year = count(facets.seasonYear) ?? (result.releaseDate ? Number(result.releaseDate.slice(0, 4)) : null)

  const lead =
    nextEpisode !== null && until
      ? `Ep ${nextEpisode} airing in ${until}`
      : [season ? seasonWord(season) : null, year].filter(Boolean).join(' ') || null

  const right = anchor.right + GAP + PEEK_WIDTH <= window.innerWidth
  const style = {
    top: Math.max(GAP, anchor.top),
    left: right ? anchor.right + GAP : anchor.left - GAP - PEEK_WIDTH,
    width: PEEK_WIDTH,
  }

  return createPortal(
    <div className={right ? 'card-peek' : 'card-peek is-left'} style={style} role="tooltip">
      <div className="peek-head">
        {lead && <span className="peek-lead">{lead}</span>}
        {score !== null && (
          <span className="peek-score">
            <ScoreFace score={score} />
            {score}%
          </span>
        )}
      </div>

      {studio && <span className="peek-studio">{studio}</span>}

      {(format || episodes !== null) && (
        <span className="peek-format">
          {[format ? formatWord(format) : null, episodes !== null ? `${episodes} ${unit}${episodes === 1 ? '' : 's'}` : null]
            .filter(Boolean)
            .join(' · ')}
        </span>
      )}

      {genres.length > 0 && (
        <ul className="peek-genres">
          {genres.map((genre) => (
            <li key={genre}>{genre.toLowerCase()}</li>
          ))}
        </ul>
      )}
    </div>,
    document.body,
  )
}
