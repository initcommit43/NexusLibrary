import { Link } from 'react-router-dom'
import { AppShell } from '../components/AppShell'
import { NotificationList } from '../components/NotificationList'
import { useNotifications } from '../components/useNotifications'
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
  const { waiting, loading, read, readAll } = useNotifications(mediaTypesOf(module), ALL_OF_IT)

  return (
    <AppShell>
      <h1>
        {module.label} notifications
        {waiting.unread > 0 && (
          <button
            type="button"
            className="section-action ghost small"
            onClick={() => void readAll()}
          >
            Read all
          </button>
        )}
      </h1>

      {loading && <p className="muted">Loading…</p>}

      {!loading && waiting.items.length === 0 && (
        <p className="muted">
          Nothing yet. An episode airing, or a season appearing, turns up here.{' '}
          <Link to={`/search?module=${module.slug}`}>Track something</Link> and it starts.
        </p>
      )}

      <NotificationList notifications={waiting.items} onRead={(id) => void read(id)} />
    </AppShell>
  )
}
