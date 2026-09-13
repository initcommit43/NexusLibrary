import { useNavigate } from 'react-router-dom'
import { BackButton } from './BackButton'
import { FeedTitle } from './FeedPicker'
import { FEED_LABELS, FEED_PATHS, type FeedKind } from './feedKind'
import { SegmentedControl } from './SegmentedControl'
import { useNarrowScreen } from './useNarrowScreen'

/**
 * The head of the notifications and activity pages, which are two views of one screen: the
 * title with the list's one action level with it, and the way across to the other list.
 *
 * <p>Wide, the title itself opens onto the other list. On a phone the switch is a segmented
 * control under a plain title instead, and it replaces the page in the history rather than
 * adding to it, so Back leaves the screen instead of flipping between the two.
 */
export const FeedHead = ({
  current,
  unread = 0,
  onReadAll,
}: {
  current: FeedKind
  unread?: number
  /** Marks everything read; the page without one has no action to show. */
  onReadAll?: () => void
}) => {
  const narrow = useNarrowScreen()
  const navigate = useNavigate()

  const go = (kind: FeedKind, replace: boolean) => {
    if (kind !== current) navigate(FEED_PATHS[kind], { replace })
  }

  /*
   * Kept where it is while nothing is unread on a phone, greyed, so the heading does not change
   * shape as the list is read; wide, it goes, as it always has.
   */
  const action =
    onReadAll &&
    (narrow ? (
      <button
        type="button"
        className="feed-read-all"
        disabled={unread === 0}
        onClick={onReadAll}
      >
        Mark all read
      </button>
    ) : (
      unread > 0 && (
        <button type="button" className="section-action ghost small" onClick={onReadAll}>
          Read all
        </button>
      )
    ))

  return (
    <>
      {narrow && <BackButton />}

      <div className="feed-head">
        {narrow ? (
          <h1 className="page-title">{FEED_LABELS[current]}</h1>
        ) : (
          <FeedTitle current={current} unread={unread} onChoose={(kind) => go(kind, false)} />
        )}
        {action}
      </div>

      {narrow && (
        <SegmentedControl
          label="Notifications or activity"
          value={current}
          onChange={(kind) => go(kind, true)}
          options={[
            { value: 'notifications', label: FEED_LABELS.notifications },
            { value: 'activity', label: FEED_LABELS.activity },
          ]}
        />
      )}
    </>
  )
}
