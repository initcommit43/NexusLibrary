import type { TrackedItem } from '../api/client'
import type { ListColumn } from '../modules/registry'
import { array, text } from './json'
import { progressSummary } from './progress'
import { toDisplayScore } from './rating'

/**
 * AniList and TMDB name formats in their own enum spelling. The common ones read better
 * written out; anything else falls back to the token with its underscores dropped.
 */
const FORMAT_WORDS: Record<string, string> = {
  TV: 'TV',
  TV_SHORT: 'TV Short',
  MOVIE: 'Movie',
  SPECIAL: 'Special',
  OVA: 'OVA',
  ONA: 'ONA',
  MUSIC: 'Music',
  MANGA: 'Manga',
  NOVEL: 'Novel',
  ONE_SHOT: 'One Shot',
}

/** A source's format token written out for reading, wherever a format is shown. */
export const formatWord = (format: string): string => FORMAT_WORDS[format] ?? format.replaceAll('_', ' ')

const formatOf = (entry: TrackedItem) => {
  const format = text(entry.metadata.format)
  return format ? formatWord(format) : null
}

const countOf = (value: unknown) => (typeof value === 'number' ? String(value) : null)

/**
 * Seasons fully watched out of the show's total, read off episode progress against the
 * per-season episode counts. Just the total where there is no breakdown or no progress yet.
 */
const seasonsOf = (entry: TrackedItem) => {
  const perSeason = array(entry.metadata.seasonEpisodes).filter(
    (count): count is number => typeof count === 'number',
  )
  if (perSeason.length === 0 || entry.progressCurrent === null) {
    return countOf(entry.metadata.seasons)
  }

  let remaining = entry.progressCurrent
  let watched = 0
  for (const episodes of perSeason) {
    if (remaining < episodes) break
    remaining -= episodes
    watched += 1
  }
  return `${watched} / ${perSeason.length}`
}

export const LIST_COLUMNS: Record<
  ListColumn,
  { label: string; read: (entry: TrackedItem) => string | null }
> = {
  score: { label: 'Score', read: (entry) => toDisplayScore(entry.rating) },
  progress: { label: 'Progress', read: progressSummary },
  hours: { label: 'Hours', read: progressSummary },
  format: { label: 'Format', read: formatOf },
  seasons: { label: 'Seasons', read: seasonsOf },
  runtime: {
    label: 'Runtime',
    read: (entry) => {
      const minutes = entry.metadata.runtimeMinutes
      return typeof minutes === 'number' && minutes > 0 ? `${minutes} min` : null
    },
  },
  year: { label: 'Year', read: (entry) => entry.releaseDate?.slice(0, 4) ?? null },
  author: { label: 'Author', read: (entry) => text(array(entry.metadata.authors)[0]) },
}

/** Units counted one at a time, where a "+1" is a thing a reader does after every sitting. */
export const STEPPED_UNITS = new Set(['EPISODES', 'CHAPTERS'])
