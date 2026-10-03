import type { CSSProperties, ReactNode } from 'react'
import { Heart } from './Heart'
import type { Summary } from './stats'

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
