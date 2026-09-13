import { FEED_LABELS, type FeedKind } from './feedKind'
import { useMenuDismiss } from './useMenuDismiss'

export type { FeedKind } from './feedKind'

const ChevronIcon = () => (
  <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 9 6 6 6-6" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

/** The two lists, the one showing marked, and how much is waiting beside Notifications. */
const FeedOptions = ({
  current,
  unread,
  onChoose,
}: {
  current: FeedKind
  unread: number
  onChoose: (kind: FeedKind) => void
}) => (
  <ul className="feed-options" role="menu">
    {(Object.keys(FEED_LABELS) as FeedKind[]).map((kind) => (
      <li key={kind} role="none">
        <button
          type="button"
          role="menuitem"
          className={kind === current ? 'chosen' : undefined}
          onClick={() => onChoose(kind)}
        >
          {FEED_LABELS[kind]}
          {kind === 'notifications' && unread > 0 && <span className="feed-unread">{unread}</span>}
        </button>
      </li>
    ))}
  </ul>
)

/**
 * The section's own name, and the other list behind it.
 *
 * <p>Two lists rather than two sections: they answer the same question from either side —
 * what has happened lately, by me and to what I keep — and both are read in the same glance
 * down the same column. A heading that opens is what says so without spending a second
 * heading's worth of the page on it.
 */
export const FeedPicker = ({
  current,
  unread,
  onChoose,
}: {
  current: FeedKind
  /** How much is waiting, shown against the list it is waiting in rather than in the open. */
  unread: number
  onChoose: (kind: FeedKind) => void
}) => {
  const { open, setOpen, container } = useMenuDismiss<HTMLSpanElement>()

  return (
    <span className="feed-picker" ref={container}>
      <button
        type="button"
        className="feed-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        {FEED_LABELS[current]}
        {current === 'activity' && unread > 0 && <span className="feed-unread">{unread}</span>}
        <ChevronIcon />
      </button>

      {open && (
        <FeedOptions
          current={current}
          unread={unread}
          onChoose={(kind) => {
            onChoose(kind)
            setOpen(false)
          }}
        />
      )}
    </span>
  )
}

/**
 * The same two lists as a page's title, the way Home's title opens the modules on a phone: the
 * h1 holds only the name, and the menu sits beside it so its options are never read as the
 * page's name.
 */
export const FeedTitle = ({
  current,
  unread = 0,
  onChoose,
}: {
  current: FeedKind
  unread?: number
  onChoose: (kind: FeedKind) => void
}) => {
  const { open, setOpen, container, trigger } = useMenuDismiss<HTMLDivElement, HTMLButtonElement>()

  return (
    <div className="feed-picker feed-title" ref={container}>
      <h1 className="page-title">
        <button
          ref={trigger}
          type="button"
          className="title-trigger"
          aria-haspopup="menu"
          aria-expanded={open}
          onClick={() => setOpen((wasOpen) => !wasOpen)}
        >
          <span>{FEED_LABELS[current]}</span>
          <ChevronIcon />
        </button>
      </h1>

      {open && (
        <FeedOptions
          current={current}
          unread={unread}
          onChoose={(kind) => {
            setOpen(false)
            onChoose(kind)
          }}
        />
      )}
    </div>
  )
}
