import { useId, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

/** The arrow at the end of a row that opens something further. */
export const ChevronRight = () => (
  <svg
    className="chevron-right"
    viewBox="0 0 24 24"
    width="14"
    height="14"
    fill="none"
    stroke="currentColor"
    aria-hidden
  >
    <path d="m9 6 6 6-6 6" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

/**
 * Rows in one rounded card under a quiet label, the way a phone lays out settings and feeds.
 * The styles exist at a phone's width only.
 */
export const Group = ({ label, children }: { label?: ReactNode; children: ReactNode }) => {
  const id = useId()

  return (
    <section className="group-section" aria-labelledby={label ? id : undefined}>
      {label && (
        <h2 id={id} className="group-label">
          {label}
        </h2>
      )}
      <ul className="group">{children}</ul>
    </section>
  )
}

/**
 * A row of a {@link Group}. With {@code to} it is a link and with {@code onClick} a button;
 * otherwise it only holds what is in it, such as a switch in {@code trailing}.
 */
export const GroupRow = ({
  title,
  subtitle,
  thumb,
  trailing,
  danger = false,
  to,
  onClick,
  disabled,
}: {
  title: ReactNode
  subtitle?: ReactNode
  /** A cover or picture before the text; give it the class group-row-thumb. */
  thumb?: ReactNode
  /** A time, a switch or a {@link ChevronRight}. */
  trailing?: ReactNode
  danger?: boolean
  to?: string
  onClick?: () => void
  disabled?: boolean
}) => {
  const className = ['group-row', subtitle ? 'is-two-line' : '', danger ? 'danger' : '']
    .filter(Boolean)
    .join(' ')

  const content = (
    <>
      {thumb}
      <span className="group-row-body">
        <span className="group-row-text">
          <span className="group-row-title">{title}</span>
          {subtitle && <span className="group-row-subtitle">{subtitle}</span>}
        </span>
        {trailing && <span className="group-row-trailing">{trailing}</span>}
      </span>
    </>
  )

  if (to) {
    return (
      <li>
        <Link className={className} to={to}>
          {content}
        </Link>
      </li>
    )
  }

  if (onClick) {
    return (
      <li>
        <button type="button" className={className} disabled={disabled} onClick={onClick}>
          {content}
        </button>
      </li>
    )
  }

  return <li className={className}>{content}</li>
}
