import type { TrackedItem } from '../api/client'

/**
 * Playtime is stored in minutes because that is what Steam reports, and rounding to hours
 * on write would throw away precision we cannot get back. Hours are purely a presentation
 * choice — which is what a future settings toggle will switch.
 */
export const MINUTES_PER_HOUR = 60

/**
 * Where a title stands, in its own unit: "191 h", "7 / 12", "41".
 *
 * <p>Shared by the shelf card and the home column rather than written twice — the two would
 * drift, and a game reading "191 h" in one place and "11460" in the other is the kind of
 * thing nobody notices until it is everywhere.
 */
export const progressSummary = (entry: TrackedItem): string | null => {
  if (entry.progressCurrent === null) return null
  if (entry.progressUnit === 'MINUTES') {
    return `${Math.round(entry.progressCurrent / MINUTES_PER_HOUR)} h`
  }
  return entry.progressMax
    ? `${entry.progressCurrent} / ${entry.progressMax}`
    : String(entry.progressCurrent)
}

/**
 * How many aired episodes are waiting, or null where the mark does not belong.
 *
 * <p>The stored field is the *next* episode, so the last one out is the one before it. Against
 * your own progress that answers the question a dot cannot: not "is this airing" — the
 * countdown on the home page says that — but "is there something to watch tonight".
 *
 * <p>Zero is a real answer and not the same as null: a series airing that you are caught up
 * on is worth marking, just not with a number.
 */
export const episodesWaiting = (entry: TrackedItem): number | null => {
  /*
   * Airing is only half the condition. "Something to watch tonight" is a question about what
   * you are in the middle of, so a series you have not started, have parked, or have finished
   * carries no mark — a planned show mid-season would otherwise wear the entire count of
   * episodes aired so far, which reads as a backlog you fell behind on rather than one you
   * never began. The home page's airing shelf filtered for this itself; every other caller
   * had no gate at all, so the rule lives here now and holds for all of them.
   */
  if (entry.status !== 'IN_PROGRESS') return null

  const next = entry.metadata.nextEpisode
  if (typeof next !== 'object' || next === null) return null

  const { episode } = next as Record<string, unknown>
  if (typeof episode !== 'number') return null

  const aired = episode - 1
  const watched = entry.progressCurrent ?? 0
  return Math.max(aired - watched, 0)
}

export const progressLabel = (entry: TrackedItem) =>
  entry.progressUnit === 'MINUTES' || entry.progressUnit === null
    ? 'Playtime (hours)'
    : `Progress (${entry.progressUnit.toLowerCase()})`

/** The short word a count is read with on a library row, as the native app writes it. */
const UNIT_WORDS: Record<string, string> = {
  EPISODES: 'Ep',
  CHAPTERS: 'Ch',
  PAGES: 'p.',
}

/**
 * Where a title stands, as a line of words: "Ep 12 of 28", "Ch 41", "191 h".
 *
 * <p>For the library's list rows and phone cards, which give progress a line of its own where
 * a desktop card squeezes it beside the score. A unit with no word here falls back to
 * {@link progressSummary}, so a new unit reads as a bare count rather than as nothing.
 */
export const progressLine = (entry: TrackedItem): string | null => {
  const word = entry.progressUnit ? UNIT_WORDS[entry.progressUnit] : undefined
  if (entry.progressCurrent === null || !word) return progressSummary(entry)
  return entry.progressMax
    ? `${word} ${entry.progressCurrent} of ${entry.progressMax}`
    : `${word} ${entry.progressCurrent}`
}

/** How far through a title is, from 0 to 1, or null where there is no total to measure by. */
export const progressShare = (entry: TrackedItem): number | null => {
  if (entry.progressCurrent === null || !entry.progressMax || entry.progressUnit === 'MINUTES') {
    return null
  }
  return Math.min(entry.progressCurrent / entry.progressMax, 1)
}
