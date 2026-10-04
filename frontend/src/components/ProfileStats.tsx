import type { CSSProperties, ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { ActivityDay, TrackingStatus } from '../api/client'
import { PLAIN_STATUS } from '../modules/registry'
import { Heart } from './Heart'
import type { StatusSlice, Summary, TimeSpent } from './stats'

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']

/**
 * Finished first and dropped last: the bar reads from what was got through outwards, so the
 * longest run on most shelves starts every row at the same edge.
 */
const SHELF_ORDER: TrackingStatus[] = ['COMPLETED', 'IN_PROGRESS', 'PLANNING', 'PAUSED', 'DROPPED']

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
 * The map beside it, added up by month: everything started, finished or logged.
 *
 * <p>Not titles finished, which it once counted: hardly anyone finishes several series or
 * films a month, so most bars stood at nought or one and the chart said nothing. The month
 * still running is drawn paler, since its bar is not done growing.
 */
export const ActivityByMonth = ({ days }: { days: ActivityDay[] }) => {
  const today = new Date()
  const months = Array.from({ length: MONTHS_SHOWN }, (_, back) => {
    const at = new Date(today.getFullYear(), today.getMonth() - (MONTHS_SHOWN - 1 - back), 1)
    return { year: at.getFullYear(), month: at.getMonth(), amount: 0 }
  })

  for (const day of days) {
    // A calendar date, split rather than parsed: Date would read it as UTC midnight and
    // move the last of the month into the next for anyone west of Greenwich.
    const [year, month] = day.date.split('-').map(Number)
    const bucket = months.find((held) => held.year === year && held.month === month - 1)
    if (bucket) bucket.amount += day.amount
  }

  const most = Math.max(1, ...months.map((held) => held.amount))

  return (
    <div className="activity-months">
      <span className="activity-caption activity-months-title">Per month</span>
      <div className="activity-months-bars">
        {months.map((held, at) => (
          <div key={`${held.year}-${held.month}`} className="activity-month">
            <span className="activity-month-amount">{held.amount}</span>
            <span
              className={at === MONTHS_SHOWN - 1 ? 'activity-month-bar is-running' : 'activity-month-bar'}
              style={{ '--share': held.amount / most } as CSSProperties}
              title={`${MONTHS[held.month]} ${held.year} · ${held.amount} logged`}
            />
          </div>
        ))}
      </div>
      <div className="activity-months-axis">
        {months.map((held) => (
          <span key={`${held.year}-${held.month}`}>{MONTHS[held.month]}</span>
        ))}
      </div>
    </div>
  )
}

export interface ShelfLine {
  key: string
  path: string
  label: string
  summary: Summary
  split: StatusSlice[]
  time: TimeSpent | null
}

/** The lowest score the track starts at, unless a shelf sits under it. */
const TRACK_FLOOR = 5

/**
 * Every shelf as one row: what it holds by status, how many, how it scores, how much of it
 * was got through.
 *
 * <p>Status bars share one scale — the fullest shelf's — so a short bar is a small shelf, not
 * a shelf drawn smaller. Scores sit on one track against the reader's overall average, which
 * is what turns six means into a reading of which shelves they like more.
 */
export const ShelfTable = ({ shelves, average }: { shelves: ShelfLine[]; average: number | null }) => {
  const fullest = Math.max(1, ...shelves.map((shelf) => shelf.summary.tracked))
  const means = shelves.flatMap((shelf) =>
    shelf.summary.meanScore === null ? [] : [shelf.summary.meanScore],
  )
  const floor = Math.min(TRACK_FLOOR, Math.floor(Math.min(10, ...means)))
  const along = (score: number) => ((score - floor) / (10 - floor)) * 100

  // A key to a status no bar draws is a key to nothing.
  const legend = SHELF_ORDER.filter((status) =>
    shelves.some((shelf) => shelf.split.some((slice) => slice.status === status && slice.amount > 0)),
  )

  return (
    <section className="profile-panel is-outlined">
      <div className="profile-panel-head">
        <div className="profile-panel-title">
          <h2>Your library</h2>
          <Link to="/stats">All stats →</Link>
        </div>
        <ul className="shelf-legend">
          {legend.map((status) => (
            <li key={status}>
              <span className="shelf-swatch" data-status={status} aria-hidden />
              {PLAIN_STATUS[status]}
            </li>
          ))}
        </ul>
      </div>

      <div className="shelf-table-scroll">
        <table className="shelf-table">
          <colgroup>
            <col className="shelf-col-name" />
            <col />
            <col className="shelf-col-count" />
            <col className="shelf-col-score" />
            <col className="shelf-col-time" />
          </colgroup>
          <thead>
            <tr>
              <th scope="col">Shelf</th>
              <th scope="col">By status</th>
              <th scope="col">Titles</th>
              <th scope="col">
                <span className="shelf-score-head">
                  <span>Avg score</span>
                  <span className="shelf-score-range">{floor} – 10</span>
                </span>
              </th>
              <th scope="col" className="is-numeric">Got through</th>
            </tr>
          </thead>
          <tbody>
            {shelves.map(({ key, path, label, summary, split, time }) => {
              const order = SHELF_ORDER.flatMap((status) =>
                split.filter((slice) => slice.status === status && slice.amount > 0),
              )
              const said = order
                .map((slice) => `${slice.amount} ${PLAIN_STATUS[slice.status].toLowerCase()}`)
                .join(', ')
              const mean = summary.meanScore

              return (
                <tr key={key}>
                  <th scope="row">
                    <Link to={path}>{label}</Link>
                  </th>
                  <td>
                    <div className="shelf-bar" role="img" aria-label={said || 'Empty'}>
                      {order.map((slice) => (
                        <span
                          key={slice.status}
                          data-status={slice.status}
                          style={{ width: `${(slice.amount / fullest) * 100}%` }}
                          title={`${PLAIN_STATUS[slice.status]} · ${slice.amount.toLocaleString()}`}
                        />
                      ))}
                    </div>
                  </td>
                  <td className="shelf-count">{summary.tracked.toLocaleString()}</td>
                  <td>
                    <div className="shelf-track">
                      {average !== null && (
                        <span className="shelf-track-average" style={{ left: `${along(average)}%` }} />
                      )}
                      {mean !== null && (
                        <>
                          <span className="shelf-track-dot" style={{ left: `${along(mean)}%` }} />
                          {/* Past the dot, unless that would run it into the next column. */}
                          <span
                            className={along(mean) > 80 ? 'shelf-track-label is-before' : 'shelf-track-label'}
                            style={{ left: `${along(mean)}%` }}
                          >
                            {mean.toFixed(1)}
                          </span>
                        </>
                      )}
                    </div>
                  </td>
                  <td className="is-numeric">
                    {time === null ? (
                      <span className="muted">—</span>
                    ) : (
                      <>
                        <strong>{time.amount.toLocaleString()}</strong>{' '}
                        <span className="muted">{time.unit}</span>
                      </>
                    )}
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {average !== null && (
        <p className="shelf-table-note">Dashed line: your overall average, {average.toFixed(1)}.</p>
      )}
    </section>
  )
}
