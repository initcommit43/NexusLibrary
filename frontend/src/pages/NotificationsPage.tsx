import { Link } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { FeedHead } from '../components/FeedHead'
import { NotificationGroups } from '../components/FeedGroups'
import { NotificationList } from '../components/NotificationList'
import { FeedGroupSkeleton, FeedSkeleton } from '../components/Skeleton'
import { useNotifications } from '../components/useNotifications'
import { useNarrowScreen } from '../components/useNarrowScreen'
import { mediaTypesOf } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

/**
 * The whole of what is waiting, where the panel on the home page holds the last of it.
 *
 * <p>No "load more" under it: what is waiting is bounded by what happened while the reader
 * was away, which is a page or two and not the years an activity feed goes back.
 */
const ALL_OF_IT = 200

export const NotificationsPage = () => {
  const module = useCurrentModule()
  const { waiting, loading, failed, read, readAll } = useNotifications(mediaTypesOf(module), ALL_OF_IT)
  const narrow = useNarrowScreen()

  return (
    <AppShell>
      <FeedHead current="notifications" unread={waiting.unread} onReadAll={() => void readAll()} />

      {/* The home panel stays quiet when this fails; the page that is nothing but the list
          cannot, or an outage reads as an empty inbox. */}
      {failed && (
        <p className="alert" role="alert">
          Could not load your notifications.
        </p>
      )}

      {loading && (narrow ? <FeedGroupSkeleton rows={8} /> : <FeedSkeleton rows={8} />)}

      {!loading && !failed && waiting.items.length === 0 && (
        <p className="muted">
          Nothing yet. An episode airing, or a season appearing, turns up here.{' '}
          <Link to={`/search?module=${module.slug}`}>Track something</Link> and it starts.
        </p>
      )}

      {narrow ? (
        <NotificationGroups notifications={waiting.items} onRead={(id) => void read(id)} />
      ) : (
        <NotificationList notifications={waiting.items} onRead={(id) => void read(id)} />
      )}
    </AppShell>
  )
}
