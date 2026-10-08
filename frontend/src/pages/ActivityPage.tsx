import { Link } from 'react-router-dom'
import { ActivityFeed } from '../components/ActivityFeed'
import { AppShell } from '../components/AppShell'
import { FeedHead } from '../components/FeedHead'
import { ActivityGroups } from '../components/FeedGroups'
import { FeedGroupSkeleton, FeedSkeleton } from '../components/Skeleton'
import { useActivityFeed } from '../components/useActivityFeed'
import { useNarrowScreen } from '../components/useNarrowScreen'
import { useCurrentModule } from '../modules/useCurrentModule'

/** A page of the feed, and what one press of "Load more" adds to it. */
const PAGE_ROWS = 25

export const ActivityPage = () => {
  const module = useCurrentModule()
  const { rows, hasMore, more, forget, loading, error } = useActivityFeed(module.slug, PAGE_ROWS)
  const narrow = useNarrowScreen()

  return (
    <AppShell>
      {/* Nothing here is ever unread, so there is no action beside the title. */}
      <FeedHead current="activity" />

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {loading &&
        !error &&
        (narrow ? <FeedGroupSkeleton rows={8} /> : <FeedSkeleton rows={8} />)}

      {!loading && rows.length === 0 && (
        <p className="muted">
          Nothing yet.{' '}
          <Link to={`/search?module=${module.slug}`}>Track something</Link> and your history
          shows up here.
        </p>
      )}

      {narrow ? (
        <ActivityGroups feed={rows} onForget={(id) => void forget(id)} />
      ) : (
        <ActivityFeed feed={rows} onForget={(id) => void forget(id)} />
      )}

      {rows.length > 0 && hasMore && (
        <button type="button" className="ghost feed-more" onClick={more}>
          Load more
        </button>
      )}
    </AppShell>
  )
}
