import type { CSSProperties, ReactNode } from 'react'
import type { TrackedItem } from '../api/client'
import { Heart } from './Heart'
import type { Summary } from './stats'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

const percent = (part: number, whole: number) => (whole === 0 ? 0 : Math.round((part / whole) * 100))

/** A ring filled to a share, with what it says in the middle. */
const Ring = ({ share, children }: { share: number; children: ReactNode }) => (
  <div className="profile-ring" style={{ '--ring-share': `${share}%` } as CSSProperties}>
    <span className="profile-ring-hole">{children}</span>
  </div>
)

const Tally = ({ value, aside, label }: { value: string; aside?: string; label: string }) => (
  <div className="profile-tally">
    <span className="profile-tally-value">
      {value} {aside && <span className="profile-tally-aside">{aside}</span>}
    </span>
    <span className="profile-tally-label">{label}</span>
  </div>
)

/**
 * The library's four headline shares as rings across the top of the page.
 *
 * <p>A ring rather than a bare numeral because each of these is a part of something: done of
 * what is tracked, rated of the library, a score of ten. Favourites are a count of nothing in
 * particular, so their ring is closed and carries the mark instead of a share.
 */
export const ProfileRings = ({ totals, favourites }: { totals: Summary; favourites: number }) => {
  const score = totals.meanScore === null ? '—' : totals.meanScore.toFixed(1)

  return (
    <div className="profile-rings">
      <div className="profile-ring-cell">
        <Ring share={percent(totals.completed, totals.tracked)}>
          {percent(totals.completed, totals.tracked)}%
        </Ring>
        <Tally
          value={totals.completed.toLocaleString()}
          aside={`of ${totals.tracked.toLocaleString()}`}
          label="completed"
        />
      </div>
      <div className="profile-ring-cell">
        <Ring share={percent(totals.rated, totals.tracked)}>
          {percent(totals.rated, totals.tracked)}%
        </Ring>
        <Tally value={totals.rated.toLocaleString()} aside="rated" label="of the library rated" />
      </div>
      <div className="profile-ring-cell">
        <Ring share={totals.meanScore === null ? 0 : totals.meanScore * 10}>{score}</Ring>
        <Tally value={score} aside="/ 10" label="average score" />
      </div>
      <div className="profile-ring-cell">
        <div className="profile-ring is-closed">
          <Heart filled={false} size={22} />
        </div>
        <Tally value={favourites.toLocaleString()} label="favourites" />
      </div>
    </div>
  )
}

/** How many months the bars cover, this one included. */
const MONTHS_SHOWN = 7

/**
 * Titles finished in each of the last months, read off the entries' own finish dates.
 *
 * <p>Not off the map beside it: a day on the map is anything started, finished or logged, and
 * a bar labelled finished must count only that. The month still running is drawn paler,
 * since its bar is not done growing.
 */
export const FinishedByMonth = ({ entries }: { entries: TrackedItem[] }) => {
  const today = new Date()
  const months = Array.from({ length: MONTHS_SHOWN }, (_, back) => {
    const at = new Date(today.getFullYear(), today.getMonth() - (MONTHS_SHOWN - 1 - back), 1)
    return { year: at.getFullYear(), month: at.getMonth(), amount: 0 }
  })

  for (const entry of entries) {
    if (!entry.finishedAt) continue
    // A calendar date, split rather than parsed: Date would read it as UTC midnight and
    // move the last of the month into the next for anyone west of Greenwich.
    const [year, month] = entry.finishedAt.split('-').map(Number)
    const bucket = months.find((held) => held.year === year && held.month === month - 1)
    if (bucket) bucket.amount += 1
  }

  const most = Math.max(1, ...months.map((held) => held.amount))

  return (
    <div className="finished-months">
      <span className="finished-months-title">Finished per month</span>
      <div className="finished-months-bars">
        {months.map((held, at) => (
          <div key={`${held.year}-${held.month}`} className="finished-month">
            <span className="finished-month-amount">{held.amount}</span>
            <span
              className={at === MONTHS_SHOWN - 1 ? 'finished-month-bar is-running' : 'finished-month-bar'}
              style={{ '--share': held.amount / most } as CSSProperties}
              title={`${MONTHS[held.month]} ${held.year} · ${held.amount} finished`}
            />
          </div>
        ))}
      </div>
      <div className="finished-months-axis">
        {months.map((held) => (
          <span key={`${held.year}-${held.month}`}>{MONTHS[held.month]}</span>
        ))}
      </div>
    </div>
  )
}
