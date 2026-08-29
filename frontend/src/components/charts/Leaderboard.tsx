import { Link } from 'react-router-dom'

export interface BoardRow {
  id: number
  title: string
  coverUrl: string | null
  /** Where the title's own page is; a leaderboard names things worth going back to. */
  to: string
  amount: number
  /** The amount said aloud — "191 h", "1,100 episodes" — beside the bar that draws it. */
  figure: string
}

/**
 * The titles that took the most time, each wearing its cover.
 *
 * <p>The one place on the stats pages an image appears, and so the one place a framed box
 * is allowed — the frame belongs to the artwork, not the row. Everything else is the usual
 * hairline arithmetic: fixed columns so ten bars start at one x, and a figure stated in the
 * medium's own unit rather than a bare number.
 */
export const Leaderboard = ({ rows }: { rows: BoardRow[] }) => {
  if (rows.length === 0) return null
  const peak = Math.max(...rows.map((row) => row.amount))

  return (
    <ol className="board">
      {rows.map((row, index) => (
        <li key={row.id}>
          <span className="board-rank">{index + 1}</span>
          {row.coverUrl ? (
            <img className="board-cover" src={row.coverUrl} alt="" loading="lazy" />
          ) : (
            <span className="board-cover blank" aria-hidden="true" />
          )}
          <Link className="board-title" to={row.to} title={row.title}>
            {row.title}
          </Link>
          <span className="board-track" aria-hidden="true">
            <span
              className="board-fill"
              style={{ width: `${Math.round((row.amount / peak) * 100)}%` }}
            />
          </span>
          <span className="board-figure">{row.figure}</span>
        </li>
      ))}
    </ol>
  )
}
