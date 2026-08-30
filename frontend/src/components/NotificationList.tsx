import { Link } from 'react-router-dom'
import type { NotificationEntry } from '../api/client'
import { relative } from './activity'
import { mediaPathFor } from '../modules/registry'

/**
 * What a notification says, in the source's own terms.
 *
 * <p>The title is the link and the rest is the sentence around it, so the row reads as one
 * line rather than as a label with a title bolted on: "Episode 9 of Sekirei aired".
 */
const sentence = (notification: NotificationEntry) => {
  const title = (
    <Link className="notification-title" to={mediaPathFor(notification)}>
      {notification.payload.title ?? notification.title}
    </Link>
  )

  if (notification.type === 'EPISODE_AIRED') {
    return (
      <>
        Episode {notification.payload.episode} of {title} aired
      </>
    )
  }
  return <>{title} was added</>
}

/**
 * What happened while the reader was away, newest first.
 *
 * <p>Unread rows carry the accent down their edge and a lighter ground: a list where new and
 * seen look alike is a list nobody scans twice. Reading them is one button, because the thing
 * a reader does with a page of these is glance at it and be done.
 */
export const NotificationList = ({
  notifications,
}: {
  notifications: NotificationEntry[]
}) => (
  <ul className="activity-feed">
    {notifications.map((notification) => (
      <li
        key={notification.id}
        className={notification.read ? 'activity-row' : 'activity-row is-new'}
      >
        {notification.coverUrl ? (
          <img src={notification.coverUrl} alt="" loading="lazy" />
        ) : (
          <div className="activity-thumb-placeholder" aria-hidden="true" />
        )}

        <div className="activity-text">
          <span>{sentence(notification)}</span>
        </div>

        <time className="muted" dateTime={notification.createdAt}>
          {relative(notification.createdAt)}
        </time>
      </li>
    ))}
  </ul>
)
