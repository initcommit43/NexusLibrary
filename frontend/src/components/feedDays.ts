/**
 * Days and times as a phone's feed says them: rows under the day they happened, with the time
 * beside each. All in the reader's own time zone and locale, since "yesterday" is theirs.
 */

const MINUTE = 60_000
const HOURS_PER_DAY = 24
const DAYS_PER_WEEK = 7

const dayKey = (date: Date) => `${date.getFullYear()}-${date.getMonth()}-${date.getDate()}`

const sameDay = (a: Date, b: Date) => dayKey(a) === dayKey(b)

/** "Today", "Yesterday", or the date itself, with the year only once it is not this one. */
export const dayLabel = (date: Date, now = new Date()): string => {
  if (sameDay(date, now)) return 'Today'

  const yesterday = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1)
  if (sameDay(date, yesterday)) return 'Yesterday'

  return date.toLocaleDateString(undefined, {
    day: 'numeric',
    month: 'long',
    ...(date.getFullYear() === now.getFullYear() ? {} : { year: 'numeric' }),
  })
}

/**
 * Rows grouped under the day each happened, keeping the order they came in.
 *
 * <p>Held in a map rather than cut wherever the day changes, so a row that arrives out of order
 * joins its own day instead of opening a second group under the same label.
 */
export const byDay = <T>(rows: T[], when: (row: T) => string) => {
  const days = new Map<string, { key: string; label: string; rows: T[] }>()

  for (const row of rows) {
    const date = new Date(when(row))
    const key = dayKey(date)
    const day = days.get(key)
    if (day) day.rows.push(row)
    else days.set(key, { key, label: dayLabel(date), rows: [row] })
  }

  return [...days.values()]
}

/** The clock time, "20:14", for a row already under its day. */
export const clockTime = (iso: string): string =>
  new Date(iso).toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })

/** How long ago, as short as a row's end allows: "now", "8m", "2h", "3d", "5w". */
export const shortAgo = (iso: string, now = Date.now()): string => {
  const minutes = Math.floor((now - new Date(iso).getTime()) / MINUTE)
  if (minutes < 1) return 'now'
  if (minutes < 60) return `${minutes}m`

  const hours = Math.floor(minutes / 60)
  if (hours < HOURS_PER_DAY) return `${hours}h`

  const days = Math.floor(hours / HOURS_PER_DAY)
  return days < DAYS_PER_WEEK ? `${days}d` : `${Math.floor(days / DAYS_PER_WEEK)}w`
}
