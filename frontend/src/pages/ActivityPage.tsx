import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api, type ActivityEntry } from '../api/client'
import { ActivityFeed } from '../components/ActivityFeed'
import { AppShell } from '../components/AppShell'
import { moduleOf } from '../components/activity'
import { useCurrentModule } from '../modules/useCurrentModule'

export const ActivityPage = () => {
  const module = useCurrentModule()
  const [feed, setFeed] = useState<ActivityEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .activityFeed()
      .then(setFeed)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your activity.'),
      )
  }, [])

  /*
   * One feed comes back for everything tracked; the module decides what belongs on it. A run
   * says which module it belongs to through the provider it ran against, since an import has
   * no medium of its own.
   */
  const mine = feed?.filter((entry) => moduleOf(entry)?.slug === module.slug) ?? []

  return (
    <AppShell>
      <h1>{module.label} activity</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {feed === null && !error && <p className="muted">Loading…</p>}

      {feed !== null && mine.length === 0 && (
        <p className="muted">
          Nothing yet.{' '}
          <Link to={`/search?module=${module.slug}`}>Track something</Link> and your history
          shows up here.
        </p>
      )}

      <ActivityFeed feed={mine} />
    </AppShell>
  )
}
