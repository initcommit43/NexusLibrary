import type { TooltipApi } from './useTooltip'

/** One run of the bar. Colour follows position, so keep the slices in their canonical order. */
export interface Slice {
  label: string
  amount: number
}

/**
 * A whole split into one bar rather than a row per part: five rows say five numbers, the
 * bar says one proportion, and the proportion is the reading.
 *
 * <p>Slices are coloured by their index in the list handed in — zeros included — so the
 * same status wears the same tint on every bar even where a shelf skips one. The panel owns
 * the tooltip: a group of these bars is one chart, and one chart shows one tip.
 */
export const StackedBar = ({
  label,
  slices,
  tip,
}: {
  /** Named when bars sit in a group; a lone bar's panel heading already names it. */
  label?: string
  slices: Slice[]
  tip: TooltipApi
}) => {
  const total = slices.reduce((sum, slice) => sum + slice.amount, 0)
  if (total === 0) return null

  const said = slices
    .filter((slice) => slice.amount > 0)
    .map((slice) => `${slice.amount} ${slice.label.toLowerCase()}`)
    .join(', ')

  return (
    <div className={label === undefined ? 'split-row bare' : 'split-row'}>
      {label !== undefined && <span className="split-label">{label}</span>}
      <div className="split-bar" role="img" aria-label={`${label ?? 'Status'}: ${said}`}>
        {slices.map((slice, rank) =>
          slice.amount === 0 ? null : (
            <span
              key={slice.label}
              className={`split-seg split-s${rank + 1}`}
              style={{ flexGrow: slice.amount }}
              onMouseMove={(event) =>
                tip.show(event, {
                  title: slice.label,
                  lines: [
                    `${slice.amount.toLocaleString()} titles · ${Math.round((slice.amount / total) * 100)}%`,
                  ],
                })
              }
              onMouseLeave={tip.hide}
            />
          ),
        )}
      </div>
      <span className="split-total">{total.toLocaleString()}</span>
    </div>
  )
}

/**
 * Named once over a group of bars. Each item carries its rank in the canonical order —
 * that is what keeps a chip on the tint its slices wear — and a status no bar draws sends
 * no item at all, because a key to nothing is not a key.
 */
export const SplitLegend = ({ items }: { items: { label: string; rank: number }[] }) => (
  <ul className="split-legend">
    {items.map((item) => (
      <li key={item.label}>
        <span className={`split-swatch split-s${item.rank + 1}`} aria-hidden="true" />
        {item.label}
      </li>
    ))}
  </ul>
)
