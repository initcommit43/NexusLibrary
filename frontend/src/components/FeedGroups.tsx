import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { ActivityEntry, NotificationEntry } from '../api/client'
import { mediaPathFor } from '../modules/registry'
import { describe, episodeLabel, isRun, runTip, runTitle } from './activity'
import { Tooltip } from './charts/Tooltip'
import { useTooltip } from './charts/useTooltip'
import { byDay, clockTime, shortAgo } from './feedDays'
import { Group } from './GroupedList'

/*
 * The phone's notifications and activity, as the native app lists them: rows in grouped cards
 * under the day they happened. Hand-written rows rather than GroupRow, since a row here has to
 * do what a GroupRow cannot — mark itself read as it opens, or hide a delete behind a swipe —
 * but with GroupRow's classes, so it is drawn the same.
 */

const Thumb = ({ coverUrl }: { coverUrl: string | null }) =>
  coverUrl ? (
    <img className="group-row-thumb" src={coverUrl} alt="" loading="lazy" />
  ) : (
    <span className="group-row-thumb" aria-hidden="true" />
  )

const RowBody = ({
  title,
  subtitle,
  trailing,
}: {
  title: ReactNode
  subtitle: ReactNode
  trailing: ReactNode
}) => (
  <span className="group-row-body">
    <span className="group-row-text">
      <span className="group-row-title">{title}</span>
      <span className="group-row-subtitle">{subtitle}</span>
    </span>
    <span className="group-row-trailing">{trailing}</span>
  </span>
)

/** What happened, in the desktop list's own words less the title, which the row leads with. */
const happened = (notification: NotificationEntry) =>
  notification.type === 'EPISODE_AIRED'
    ? `${episodeLabel(notification)} aired`
    : notification.type === 'RELEASE_STARTED'
      ? 'Started releasing'
      : 'Added'

export const NotificationGroups = ({
  notifications,
  onRead,
}: {
  notifications: NotificationEntry[]
  onRead: (id: number) => void
}) => (
  <div className="feed-days">
    {byDay(notifications, (notification) => notification.createdAt).map((day) => (
      <Group key={day.key} label={day.label}>
        {day.rows.map((notification) => (
          <li key={notification.id}>
            <Link
              className="group-row is-two-line"
              to={mediaPathFor(notification)}
              // Opening it is what marks it seen, as on the wide list.
              onClick={() => !notification.read && onRead(notification.id)}
            >
              <Thumb coverUrl={notification.coverUrl} />
              <RowBody
                title={notification.payload.title ?? notification.title}
                subtitle={happened(notification)}
                trailing={
                  <>
                    <time dateTime={notification.createdAt}>
                      {shortAgo(notification.createdAt)}
                    </time>
                    {!notification.read && (
                      <span className="feed-unread-dot" role="img" aria-label="Unread" />
                    )}
                  </>
                }
              />
            </Link>
          </li>
        ))}
      </Group>
    ))}
  </div>
)

/**
 * The feed under its days. Deleting an event of your own is a swipe to the left, as a phone's
 * lists do it: the row scrolls sideways onto a red Delete that was waiting past its edge. A
 * scroll rather than a drawn gesture, so the button is a real one — reached by Tab and read by
 * a screen reader after the row it belongs to — and a keyboard focusing it scrolls it into view.
 */
export const ActivityGroups = ({
  feed,
  onForget,
}: {
  feed: ActivityEntry[]
  onForget: (activityId: string) => void
}) => {
  const tip = useTooltip()

  return (
    <div className="feed-days">
      {byDay(feed, (activity) => activity.createdAt).map((day) => (
        <Group key={day.key} label={day.label}>
          {day.rows.map((activity) => {
            const run = isRun(activity)
            const title = run ? runTitle(activity) : activity.title
            const to =
              !run && activity.source && activity.externalId
                ? mediaPathFor({ source: activity.source, externalId: activity.externalId })
                : null

            const body = (
              <>
                {run ? (
                  <span className="group-row-thumb feed-run-thumb" aria-hidden="true">
                    {title?.charAt(0)}
                  </span>
                ) : (
                  <Thumb coverUrl={activity.coverUrl} />
                )}
                <RowBody
                  title={title}
                  subtitle={describe(activity)}
                  trailing={
                    <time dateTime={activity.createdAt}>{clockTime(activity.createdAt)}</time>
                  }
                />
              </>
            )

            // A run's titles open on a tap, as they open on hover and focus on the wide list.
            const showTitles = (element: HTMLElement) => {
              if ((activity.payload.titles ?? []).length === 0) return
              const box = element.getBoundingClientRect()
              tip.show({ clientX: box.left + box.width / 2, clientY: box.top }, runTip(activity))
            }

            return (
              <li key={activity.id} className="swipe-row">
                {to ? (
                  <Link className="group-row is-two-line" to={to}>
                    {body}
                  </Link>
                ) : run && (activity.payload.titles ?? []).length > 0 ? (
                  <div
                    className="group-row is-two-line"
                    tabIndex={0}
                    onClick={(event) => showTitles(event.currentTarget)}
                    onFocus={(event) => showTitles(event.currentTarget)}
                    onBlur={tip.hide}
                  >
                    {body}
                  </div>
                ) : (
                  <div className="group-row is-two-line">{body}</div>
                )}
                <button
                  type="button"
                  className="swipe-delete"
                  aria-label={`Delete this activity: ${title}`}
                  onClick={() => onForget(activity.id)}
                >
                  Delete
                </button>
              </li>
            )
          })}
        </Group>
      ))}

      <Tooltip tip={tip.tip} />
    </div>
  )
}
