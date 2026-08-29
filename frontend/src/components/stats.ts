/**
 * What a library adds up to, read off the entries the page already fetched.
 *
 * <p>Every figure here comes from the same list the shelves are drawn from — no endpoint, no
 * second request — so the arithmetic lives apart from the markup and can be read on its own.
 *
 * <p>Nothing is estimated. A count of episodes is a count of episodes; multiplying it by an
 * assumed runtime to reach "days watched" would state a guess in the voice of a fact.
 */
import type { MediaType, TrackedItem } from '../api/client'

/** How a medium counts what has been got through, and what to call it. */
export interface TimeSpent {
  amount: number
  /** Already plural: these are always counts of more than one thing, or of none. */
  unit: string
}

export interface Summary {
  tracked: number
  completed: number
  rated: number
  /** Out of ten, the scale the app speaks. Null when nothing is rated. */
  meanScore: number | null
  /** How far apart the scores sit; null under two ratings, where spread means nothing. */
  deviation: number | null
  time: TimeSpent | null
}

export interface CountRow {
  label: string
  amount: number
  /** The mean score given to the titles behind this row, out of ten. */
  meanScore: number | null
}

const RATING_SCALE = 10

const MINUTES_PER_HOUR = 60

const number = (value: unknown): number | null =>
  typeof value === 'number' && Number.isFinite(value) ? value : null

const strings = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === 'string') : []

const ratingsOf = (entries: TrackedItem[]): number[] =>
  entries.flatMap((entry) => (entry.rating === null ? [] : [entry.rating / RATING_SCALE]))

const mean = (values: number[]): number | null =>
  values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length

/**
 * How far the scores sit from their own average.
 *
 * <p>The population form rather than the sample one: this is the whole of what someone rated,
 * not a sample drawn from something larger.
 */
const deviation = (values: number[]): number | null => {
  const average = mean(values)
  if (average === null || values.length < 2) return null

  const variance = values.reduce((sum, value) => sum + (value - average) ** 2, 0) / values.length
  return Math.sqrt(variance)
}

/**
 * What has actually been got through, in the unit the medium counts in.
 *
 * <p>Progress is recorded against the entry in that unit already — episodes, chapters, pages,
 * minutes — so the only medium needing a second thought is film, where nobody tracks progress
 * through a two-hour film and the runtime of what was finished is the honest answer.
 */
export const timeSpent = (entries: TrackedItem[], mediaType: MediaType): TimeSpent | null => {
  if (mediaType === 'MOVIE') {
    const minutes = entries
      .filter((entry) => entry.status === 'COMPLETED')
      .reduce((sum, entry) => sum + (number(entry.metadata.runtimeMinutes) ?? 0), 0)

    return minutes === 0 ? null : { amount: Math.round(minutes / MINUTES_PER_HOUR), unit: 'hours watched' }
  }

  const progressed = entries.filter((entry) => entry.progressCurrent !== null)
  const total = progressed.reduce((sum, entry) => sum + (entry.progressCurrent ?? 0), 0)
  if (total === 0) return null

  const unit = progressed.find((entry) => entry.progressUnit !== null)?.progressUnit
  switch (unit) {
    case 'MINUTES':
      return { amount: Math.round(total / MINUTES_PER_HOUR), unit: 'hours played' }
    case 'CHAPTERS':
      return { amount: total, unit: 'chapters read' }
    case 'PAGES':
      return { amount: total, unit: 'pages read' }
    default:
      return { amount: total, unit: 'episodes watched' }
  }
}

export const summarise = (entries: TrackedItem[], mediaType?: MediaType): Summary => {
  const ratings = ratingsOf(entries)

  return {
    tracked: entries.length,
    completed: entries.filter((entry) => entry.status === 'COMPLETED').length,
    rated: ratings.length,
    meanScore: mean(ratings),
    deviation: deviation(ratings),
    time: mediaType ? timeSpent(entries, mediaType) : null,
  }
}

/**
 * Scores as the app shows them, in half-point steps.
 *
 * <p>Every step from the lowest given to the highest appears, including the ones nobody used:
 * a gap is the shape of how someone rates, and closing it up would draw a different picture.
 */
export const scoreBuckets = (entries: TrackedItem[]): CountRow[] => {
  const counts = new Map<number, number>()

  for (const rating of ratingsOf(entries)) {
    const step = Math.round(rating * 2) / 2
    counts.set(step, (counts.get(step) ?? 0) + 1)
  }
  if (counts.size === 0) return []

  const steps = [...counts.keys()]
  const rows: CountRow[] = []
  for (let step = Math.min(...steps); step <= Math.max(...steps); step += 0.5) {
    rows.push({ label: step.toFixed(1), amount: counts.get(step) ?? 0, meanScore: null })
  }
  return rows
}

/**
 * The years the library comes from, every year in between included.
 *
 * <p>A run of years with nothing in them says as much as the years with something: the point
 * of the panel is the shape, and a chart that skips its empty years has no shape to read.
 */
export const releaseYears = (entries: TrackedItem[]): CountRow[] => {
  const counts = new Map<number, number>()

  for (const entry of entries) {
    if (!entry.releaseDate) continue
    const year = new Date(entry.releaseDate).getFullYear()
    if (Number.isNaN(year)) continue
    counts.set(year, (counts.get(year) ?? 0) + 1)
  }
  if (counts.size === 0) return []

  const years = [...counts.keys()]
  const rows: CountRow[] = []
  for (let year = Math.min(...years); year <= Math.max(...years); year++) {
    rows.push({ label: String(year), amount: counts.get(year) ?? 0, meanScore: null })
  }
  return rows
}

/**
 * What a library is made of, by any list its items carry — genres, studios, platforms.
 *
 * <p>Each row keeps the mean score of the titles behind it, which is the number worth having:
 * a count says what someone watches, and the score beside it says what they liked.
 */
export const countBy = (entries: TrackedItem[], key: string, limit = 12): CountRow[] => {
  const groups = new Map<string, TrackedItem[]>()

  for (const entry of entries) {
    for (const value of strings(entry.metadata[key])) {
      const held = groups.get(value)
      if (held) {
        held.push(entry)
      } else {
        groups.set(value, [entry])
      }
    }
  }

  return [...groups.entries()]
    .map(([label, group]) => ({ label, amount: group.length, meanScore: mean(ratingsOf(group)) }))
    .sort((a, b) => b.amount - a.amount || a.label.localeCompare(b.label))
    .slice(0, limit)
}

/** A single-valued field — the format a title is in — counted the same way. */
export const countByValue = (entries: TrackedItem[], key: string, limit = 8): CountRow[] => {
  const groups = new Map<string, TrackedItem[]>()

  for (const entry of entries) {
    const value = entry.metadata[key]
    if (typeof value !== 'string' || !value.trim()) continue

    const held = groups.get(value)
    if (held) {
      held.push(entry)
    } else {
      groups.set(value, [entry])
    }
  }

  return [...groups.entries()]
    .map(([label, group]) => ({ label, amount: group.length, meanScore: mean(ratingsOf(group)) }))
    .sort((a, b) => b.amount - a.amount || a.label.localeCompare(b.label))
    .slice(0, limit)
}

/**
 * The list a medium is worth being broken down by beyond its genres.
 *
 * <p>Anime credits studios and books credit authors; a film's crew is on the title's own page
 * rather than in the fields core keeps, so film and TV name nothing here and their panel takes
 * itself off the page.
 */
export const creditKeyFor = (mediaType: MediaType): { key: string; title: string } | null => {
  switch (mediaType) {
    case 'ANIME':
      return { key: 'studios', title: 'Top studios' }
    case 'BOOK':
      return { key: 'authors', title: 'Most read authors' }
    case 'GAME':
      return { key: 'platforms', title: 'Platforms' }
    default:
      return null
  }
}
