import { Link } from 'react-router-dom'
import { ActivityFeed } from '../components/ActivityFeed'
import { AppShell } from '../components/AppShell'
import { useActivityFeed } from '../components/useActivityFeed'
import { useCurrentModule } from '../modules/useCurrentModule'

/** A page of the feed, and what one press of "Load more" adds to it. */
const PAGE_ROWS = 25

export const ActivityPage = () => {
  const module = useCurrentModule()
  const { rows, hasMore, more, forget, loading, error } = useActivityFeed(module.slug, PAGE_ROWS)

  return (
    <AppShell>
      <h1>{module.label} activity</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {loading && !error && <p className="muted">Loading…</p>}

      {!loading && rows.length === 0 && (
        <p className="muted">
          Nothing yet.{' '}
          <Link to={`/search?module=${module.slug}`}>Track something</Link> and your history
          shows up here.
        </p>
      )}

      <ActivityFeed feed={rows} onForget={(id) => void forget(id)} />

      {rows.length > 0 && hasMore && (
        <button type="button" className="ghost feed-more" onClick={more}>
          Load more
        </button>
      )}
    </AppShell>
  )
}
