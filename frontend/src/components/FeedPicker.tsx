import { useMenuDismiss } from './useMenuDismiss'

/** Which of the two lists the section is showing. */
export type FeedKind = 'activity' | 'notifications'

const LABELS: Record<FeedKind, string> = {
  activity: 'Activity',
  notifications: 'Notifications',
}

const ChevronIcon = () => (
  <svg viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 9 6 6 6-6" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
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
        {LABELS[current]}
        {current === 'activity' && unread > 0 && <span className="feed-unread">{unread}</span>}
        <ChevronIcon />
      </button>

      {open && (
        <ul className="feed-options" role="menu">
          {(Object.keys(LABELS) as FeedKind[]).map((kind) => (
            <li key={kind} role="none">
              <button
                type="button"
                role="menuitem"
                className={kind === current ? 'chosen' : undefined}
                onClick={() => {
                  onChoose(kind)
                  setOpen(false)
                }}
              >
                {LABELS[kind]}
                {kind === 'notifications' && unread > 0 && (
                  <span className="feed-unread">{unread}</span>
                )}
              </button>
            </li>
          ))}
        </ul>
      )}
    </span>
  )
}
