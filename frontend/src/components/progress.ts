import type { TrackedItem } from '../api/client'

/**
 * Playtime is stored in minutes because that is what Steam reports, and rounding to hours
 * on write would throw away precision we cannot get back. Hours are purely a presentation
 * choice — which is what a future settings toggle will switch.
 */
export const MINUTES_PER_HOUR = 60

export const minutesToHours = (minutes: number | null | undefined) =>
  minutes === null || minutes === undefined ? null : minutes / MINUTES_PER_HOUR

export const hoursToMinutes = (hours: number) => Math.round(hours * MINUTES_PER_HOUR)

/** What to put in an editable field: hours to one decimal, or the raw count otherwise. */
export const progressFieldValue = (entry: TrackedItem): string => {
  if (entry.progressCurrent === null) return ''
  if (entry.progressUnit === 'MINUTES') {
    return (entry.progressCurrent / MINUTES_PER_HOUR).toFixed(1)
  }
  return String(entry.progressCurrent)
}

export const progressLabel = (entry: TrackedItem) =>
  entry.progressUnit === 'MINUTES' || entry.progressUnit === null
    ? 'Playtime (hours)'
    : `Progress (${entry.progressUnit.toLowerCase()})`
