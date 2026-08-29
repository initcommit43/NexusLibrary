/**
 * What a library adds up to, read off the entries the page already fetched.
 *
 * <p>Every figure here comes from the same list the shelves are drawn from — no endpoint, no
 * second request — so the arithmetic lives apart from the markup and can be read on its own.
 *
 * <p>Nothing is estimated. A count of episodes is a count of episodes; multiplying it by an
 * assumed runtime to reach "days watched" would state a guess in the voice of a fact.
 */
import type { MediaType, TrackedItem, TrackingStatus } from '../api/client'
import { achievementProgress } from './achievements'

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
  /** The mean score this reader gave the titles behind the row, out of ten. */
  meanScore: number | null
  /** What the crowd gave them, for the shelves their reader has not rated. */
  publicScore?: number | null
}

const RATING_SCALE = 10

const MINUTES_PER_HOUR = 60

const number = (value: unknown): number | null =>
  typeof value === 'number' && Number.isFinite(value) ? value : null

const strings = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((entry): entry is string => typeof entry === 'string') : []

/** The scores this reader gave, out of ten. */
export const ownScores = (entries: TrackedItem[]): number[] =>
  entries.flatMap((entry) => (entry.rating === null ? [] : [entry.rating / RATING_SCALE]))

/**
 * The scores everyone else gave, on the same ten-point scale the reader's own use.
 *
 * <p>These are abundant where personal ratings are rare — a shelf of four books carries four
 * public scores and often no private one — so anything drawn from them must say so, but they
 * are what lets a thin shelf still show something true.
 */
export const publicScores = (entries: TrackedItem[]): number[] =>
  entries.flatMap((entry) => {
    const rating = number(entry.metadata.externalRating)
    // Zero is how a source says "not scored", not how a crowd pans something.
    return rating === null || rating <= 0 ? [] : [rating / RATING_SCALE]
  })

export const mean = (values: number[]): number | null =>
  values.length === 0 ? null : values.reduce((sum, value) => sum + value, 0) / values.length

export const median = (values: number[]): number | null => {
  if (values.length === 0) return null
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 0 ? (sorted[middle - 1] + sorted[middle]) / 2 : sorted[middle]
}

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
 * Whether a breakdown would actually divide anything.
 *
 * <p>A library whose films are all "movie" format has a fact about the field, not about the
 * library; charting it draws one full-width slice and calls it analysis. Panels ask this and
 * take themselves off the page rather than render the answer to a question nobody asked.
 */
export const discriminates = (rows: readonly unknown[], least = 2): boolean => rows.length >= least

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
  const ratings = ownScores(entries)

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
 * The one order a status can be read in: how far a title got before it stopped moving.
 * Colour ramps follow this order, which is what makes it ordinal rather than five categories.
 */
export const STATUS_ORDER: TrackingStatus[] = [
  'PLANNING',
  'IN_PROGRESS',
  'COMPLETED',
  'PAUSED',
  'DROPPED',
]

export interface StatusSlice {
  status: TrackingStatus
  amount: number
}

/**
 * Every status in pipeline order, zeros kept: a slice's colour follows its place in this
 * list, and leaving an empty shelf out would shift every colour after it between one bar
 * and the next.
 */
export const statusSplit = (entries: TrackedItem[]): StatusSlice[] =>
  STATUS_ORDER.map((status) => ({
    status,
    amount: entries.filter((entry) => entry.status === status).length,
  }))

/**
 * Scores as the app shows them, in half-point steps.
 *
 * <p>Takes the scores rather than the entries, because the same spread is drawn twice — once
 * from what the reader gave and once from what the crowd did — and the bucketing is the same
 * question either way.
 *
 * <p>Every step from the lowest given to the highest appears, including the ones nobody used:
 * a gap is the shape of how someone rates, and closing it up would draw a different picture.
 */
export const scoreBuckets = (scores: number[]): CountRow[] => {
  const counts = new Map<number, number>()

  for (const score of scores) {
    const step = Math.round(score * 2) / 2
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

/** Titles carrying both verdicts, ready to be plotted against each other. */
export interface ScorePair {
  entry: TrackedItem
  own: number
  crowd: number
}

export const scorePairs = (entries: TrackedItem[]): ScorePair[] =>
  entries.flatMap((entry) => {
    if (entry.rating === null) return []
    const crowd = number(entry.metadata.externalRating)
    return crowd === null || crowd <= 0
      ? []
      : [{ entry, own: entry.rating / RATING_SCALE, crowd: crowd / RATING_SCALE }]
  })

/** Past this many bars a year axis stops being readable and starts being a texture. */
const MAX_YEAR_BARS = 24

/** Years to a bar once a library spans more than the axis can hold one by one. */
const YEARS_PER_BUCKET = 5

/**
 * The years the library comes from, in bars an axis can carry.
 *
 * <p>Every year in the span is kept, including the ones with nothing in them: a run of empty
 * years is part of the shape, and closing the gaps draws a different picture. But a library
 * reaching back to the forties spans eighty of them, and eighty labels under a chart is a
 * smear — so past two dozen the years group into fives, and the axis names every other group
 * the way a long axis is read.
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
  const first = Math.min(...years)
  const last = Math.max(...years)
  const size = last - first < MAX_YEAR_BARS ? 1 : YEARS_PER_BUCKET

  const buckets = new Map<number, number>()
  for (const [year, amount] of counts) {
    const start = Math.floor(year / size) * size
    buckets.set(start, (buckets.get(start) ?? 0) + amount)
  }

  const firstBucket = Math.floor(first / size) * size
  const lastBucket = Math.floor(last / size) * size

  const rows: CountRow[] = []
  for (let start = firstBucket; start <= lastBucket; start += size) {
    const step = (start - firstBucket) / size
    rows.push({
      label: size === 1 || step % 2 === 0 ? String(start) : '',
      amount: buckets.get(start) ?? 0,
      meanScore: null,
    })
  }
  return rows
}

const grouped = (entries: TrackedItem[], key: string): Map<string, TrackedItem[]> => {
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
  return groups
}

const asRow = (label: string, group: TrackedItem[]): CountRow => ({
  label,
  amount: group.length,
  meanScore: mean(ownScores(group)),
  publicScore: mean(publicScores(group)),
})

/**
 * What a library is made of, by any list its items carry — genres, studios, platforms.
 *
 * <p>Each row keeps the mean of both verdicts on the titles behind it, because either can be
 * the number worth having: a count says what someone watches, their own score says what they
 * liked, and where they have not rated, the crowd's score is the one that exists.
 */
export const countBy = (entries: TrackedItem[], key: string, limit = 12): CountRow[] =>
  [...grouped(entries, key).entries()]
    .map(([label, group]) => asRow(label, group))
    .sort((a, b) => b.amount - a.amount || a.label.localeCompare(b.label))
    .slice(0, limit)

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
    .map(([label, group]) => asRow(label, group))
    .sort((a, b) => b.amount - a.amount || a.label.localeCompare(b.label))
    .slice(0, limit)
}

export interface GenrePoint {
  label: string
  amount: number
  /** Out of ten, on whichever basis the field declares. */
  score: number
}

export interface GenreField {
  points: GenrePoint[]
  /** Whose verdict the scores are — the chart must say so when it is the crowd's. */
  basis: 'own' | 'public'
  medianCount: number
  medianScore: number
}

/** A genre held once has no tendency to plot; one dot at x = 1 says "I tried it", nothing more. */
const LEAST_PER_GENRE = 2

/** Under this many personally-scored genres the field flips to the crowd's verdicts wholesale. */
const LEAST_OWN_POINTS = 5

/**
 * Each genre as a point: how much of it the library holds against how it scores.
 *
 * <p>The reader's own means are used only when enough genres carry one to make a field —
 * mixing bases per point would put one person's 8 beside a crowd's 8 as if they were the
 * same kind of number. The medians come back with the points because the quadrants they cut
 * are the reading: held often and loved, loved but rare, habit, neither.
 */
export const genrePoints = (entries: TrackedItem[]): GenreField | null => {
  const held = [...grouped(entries, 'genres').entries()].filter(
    ([, group]) => group.length >= LEAST_PER_GENRE,
  )

  const own = held.flatMap(([label, group]) => {
    const score = mean(ownScores(group))
    return score === null ? [] : [{ label, amount: group.length, score }]
  })

  const basis = own.length >= LEAST_OWN_POINTS ? 'own' : 'public'
  const points =
    basis === 'own'
      ? own
      : held.flatMap(([label, group]) => {
          const score = mean(publicScores(group))
          return score === null ? [] : [{ label, amount: group.length, score }]
        })
  if (points.length === 0) return null

  return {
    points,
    basis,
    medianCount: median(points.map((point) => point.amount)) ?? 0,
    medianScore: median(points.map((point) => point.score)) ?? 0,
  }
}

/** A leaderboard row: the entry itself, how much of it there was, and that amount said aloud. */
export interface BoardEntry {
  entry: TrackedItem
  amount: number
  figure: string
}

const hoursFigure = (minutes: number): string =>
  minutes < MINUTES_PER_HOUR
    ? `${minutes} min`
    : `${Math.round(minutes / MINUTES_PER_HOUR).toLocaleString()} h`

const runtimeFigure = (minutes: number): string =>
  `${Math.floor(minutes / MINUTES_PER_HOUR)} h ${minutes % MINUTES_PER_HOUR} min`

/**
 * The titles that took the most of whatever the medium is measured in.
 *
 * <p>Games rank by minutes played and everything episodic by its own progress count. Films
 * rank by the runtime of what was finished: nobody tracks progress through a two-hour film,
 * so the length of what was sat through is the only amount there is.
 */
export const topByProgress = (
  entries: TrackedItem[],
  mediaType: MediaType,
  limit = 8,
): BoardEntry[] =>
  entries
    .flatMap((entry): BoardEntry[] => {
      if (mediaType === 'MOVIE') {
        if (entry.status !== 'COMPLETED') return []
        const minutes = number(entry.metadata.runtimeMinutes)
        return minutes === null || minutes <= 0
          ? []
          : [{ entry, amount: minutes, figure: runtimeFigure(minutes) }]
      }

      const amount = entry.progressCurrent ?? 0
      if (amount <= 0) return []

      const figure =
        mediaType === 'GAME'
          ? hoursFigure(amount)
          : mediaType === 'MANGA'
            ? `${amount.toLocaleString()} chapters`
            : mediaType === 'BOOK'
              ? `${amount.toLocaleString()} pages`
              : `${amount.toLocaleString()} episodes`
      return [{ entry, amount, figure }]
    })
    .sort((a, b) => b.amount - a.amount)
    .slice(0, limit)

export interface AchievementTally {
  unlocked: number
  total: number
}

/**
 * Achievements across a shelf, for the one medium that has them. Steam's numbers stand in
 * where "completed" cannot: a game is rarely finished the way a film is, and the unlock
 * count is the progress its own platform believes in.
 */
export const achievementTally = (entries: TrackedItem[]): AchievementTally | null => {
  const tallies = entries.flatMap((entry) => {
    const progress = achievementProgress(entry)
    return progress === null || progress.total === 0 ? [] : [progress]
  })
  if (tallies.length === 0) return null

  return {
    unlocked: tallies.reduce((sum, tally) => sum + tally.unlocked.length, 0),
    total: tallies.reduce((sum, tally) => sum + tally.total, 0),
  }
}

/**
 * The list a medium is worth being broken down by beyond its genres.
 *
 * <p>Anime credits studios, books credit authors and games run on platforms; a film's crew
 * is on the title's own page rather than in the fields core keeps, so film and TV name
 * nothing here and their section takes itself off the page.
 */
export const creditKeyFor = (mediaType: MediaType): { key: string; title: string } | null => {
  switch (mediaType) {
    case 'ANIME':
      return { key: 'studios', title: 'Studios' }
    case 'BOOK':
      return { key: 'authors', title: 'Authors' }
    case 'GAME':
      return { key: 'platforms', title: 'Platforms' }
    default:
      return null
  }
}
