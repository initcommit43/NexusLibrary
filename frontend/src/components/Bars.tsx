import type { Distribution } from './mediaDetail'

/**
 * A count per label, drawn as bars on the page's own ground.
 *
 * <p>No frame: a border around a chart says the chart is a thing sitting on the page, when
 * the bars already are the thing. Values appear on hover, so a wall of numbers does not
 * compete with the shape they make.
 */
export const Bars = ({ rows, title }: { rows: Distribution[]; title: string }) => {
  if (rows.length === 0) return null
  const peak = Math.max(...rows.map((row) => row.amount))

  return (
    <section className="status-section">
      <h2>{title}</h2>
      <div className="bar-chart">
        {/* Keyed by place: a long axis labels only some of its bars, and blanks collide. */}
        {rows.map((row, index) => (
          <div key={`${index}-${row.label}`} className="bar">
            <span className="bar-value">{row.amount.toLocaleString()}</span>
            <div
              className="bar-fill"
              style={{ height: peak === 0 ? '0%' : `${Math.round((row.amount / peak) * 100)}%` }}
            />
            <span className="bar-label muted">{row.label}</span>
          </div>
        ))}
      </div>
    </section>
  )
}
