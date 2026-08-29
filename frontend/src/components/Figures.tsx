/** One number worth reading, and what it counts. */
export interface Figure {
  label: string
  value: string
  /** A second line under the label, for a figure that needs qualifying. */
  hint?: string | null
}

/**
 * Headline numbers on the page's own ground.
 *
 * <p>No tile around each: a border says where a number stops rather than what it means, and
 * five of them in a row cost the whole top of a page to say five short things. The size of
 * the numeral is what makes it a headline, and the space around it is what separates it from
 * the next.
 */
export const Figures = ({ figures }: { figures: Figure[] }) => {
  if (figures.length === 0) return null

  return (
    <dl className="figure-rail">
      {figures.map((figure) => (
        <div key={figure.label} className="figure">
          <dt>{figure.value}</dt>
          <dd>
            {figure.label}
            {figure.hint && <span className="muted">{figure.hint}</span>}
          </dd>
        </div>
      ))}
    </dl>
  )
}
