import type { CSSProperties } from 'react'

/*
 * Placeholders in the shape of what replaces them. Each one borrows the real component's own
 * classes for its geometry and adds only the shimmer, so the content that lands takes the
 * place it held and nothing on the page moves on the swap.
 */

const times = (count: number) => Array.from({ length: count }, (_, i) => i)

/** One shimmering block; a line of text when given no size beyond a width. */
export const Bone = ({
  width,
  height,
  className,
}: {
  width?: CSSProperties['width']
  height?: CSSProperties['height']
  className?: string
}) => (
  <span
    className={className ? `skeleton skeleton-line ${className}` : 'skeleton skeleton-line'}
    style={{ width, height }}
    aria-hidden="true"
  />
)

/** The cards of a cover grid or a sideways browse row, before the results arrive. */
export const CoverSkeletons = ({ count }: { count: number }) => (
  <>
    {times(count).map((i) => (
      <div key={i} className="card cover-card browse-skeleton" aria-hidden="true">
        <div className="cover-placeholder skeleton" />
      </div>
    ))}
  </>
)

export const CoverGridSkeleton = ({ count = 12 }: { count?: number }) => (
  <div className="cover-grid" aria-busy="true">
    <CoverSkeletons count={count} />
  </div>
)

/** Rows of the activity and notification feeds: thumb, two lines, the time at the end. */
export const FeedSkeleton = ({ rows = 6 }: { rows?: number }) => (
  <ul className="activity-feed" aria-busy="true">
    {times(rows).map((i) => (
      <li key={i} className="activity-row is-pending" aria-hidden="true">
        <span className="activity-thumb-placeholder skeleton" />
        <div className="activity-text">
          {/* Staggered widths: a column of equal bars reads as a table, not as sentences. */}
          <Bone width={`${[46, 58, 38][i % 3]}%`} />
          <Bone width={`${[28, 22, 34][i % 3]}%`} />
        </div>
        <Bone width="3rem" />
      </li>
    ))}
  </ul>
)

/** A phone's feed: one day's group of rows, with GroupRow's classes so it is drawn the same. */
export const FeedGroupSkeleton = ({ rows = 6 }: { rows?: number }) => (
  <div className="feed-days" aria-busy="true">
    <section className="group-section" aria-hidden="true">
      <h2 className="group-label">
        <Bone width="4rem" />
      </h2>
      <ul className="group">
        {times(rows).map((i) => (
          <li key={i} className="group-row is-two-line">
            <span className="group-row-thumb skeleton" />
            <span className="group-row-body">
              <span className="group-row-text">
                <Bone width={`${[62, 74, 54][i % 3]}%`} />
                <Bone width={`${[36, 28, 42][i % 3]}%`} />
              </span>
              <Bone width="1.75rem" />
            </span>
          </li>
        ))}
      </ul>
    </section>
  </div>
)

/** A Home shelf of five covers, as the gallery draws one row of a catalogue shelf. */
export const PosterRowSkeleton = () => (
  <ul className="poster-gallery one-row" aria-busy="true">
    {times(5).map((i) => (
      <li key={i} className="poster-slot" aria-hidden="true">
        <span className="poster">
          <span className="poster-blank skeleton" />
        </span>
      </li>
    ))}
  </ul>
)

/** The cards of a phone's sideways shelf: a cover with a title line under each. */
export const ShelfCardSkeletons = () => (
  <>
    {times(5).map((i) => (
      <li key={i} className="shelf-card" aria-hidden="true">
        <div className="shelf-card-cover">
          <span className="cover-placeholder skeleton" />
        </div>
        <Bone width="80%" />
      </li>
    ))}
  </>
)

/** A whole phone shelf, for when not even its title is known yet. */
export const ShelfRowSkeleton = () => (
  <section className="shelf-row-section" aria-hidden="true">
    <div className="shelf-row-head">
      <h2>
        <Bone className="skeleton-heading" width="9rem" />
      </h2>
    </div>
    <ul className="chip-row shelf-row">
      <ShelfCardSkeletons />
    </ul>
  </section>
)

/** A section heading's line, at a heading's height so its rule lands where the real one will. */
export const HeadingSkeleton = ({ width = '8rem' }: { width?: string }) => (
  <h2 aria-hidden="true">
    <Bone className="skeleton-heading" width={width} />
  </h2>
)
