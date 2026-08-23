import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api, type ActivityEntry } from '../api/client'
import { AppShell } from '../components/AppShell'
import { toDisplayScore } from '../components/rating'
import { useCurrentModule } from '../modules/useCurrentModule'
import { mediaTypesOf, statusLabelsFor } from '../modules/registry'
import type { TrackingStatus } from '../api/client'

/** Reads the stored old-to-new payload back as a sentence, in the module's own words. */
const describe = (activity: ActivityEntry): string => {
  const labels = statusLabelsFor(activity.mediaType)
  const statusLabel = (raw?: string | null) => (raw && raw in labels ? labels[raw as TrackingStatus] : raw)
  const { from, to, unit } = activity.payload

  switch (activity.type) {
    case 'ADDED':
      return `Added to ${statusLabel(activity.payload.status) ?? 'your library'}`
    case 'STATUS_CHANGE':
      return from ? `${statusLabel(from)} → ${statusLabel(to)}` : `Moved to ${statusLabel(to)}`
    case 'RATED':
      // Stored 0-100, shown on the 10-point scale the rating input uses.
      return `Rated ${toDisplayScore(Number(to))}`
    case 'PROGRESS':
      return unit === 'MINUTES'
        ? `Played ${(Number(to) / 60).toFixed(1)} hours`
        : `Progress ${from ?? 0} → ${to}`
    case 'REVIEWED':
      return 'Wrote a review'
  }
}

const relative = (iso: string) => {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return new Date(iso).toLocaleDateString()
}

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

  // One feed comes back for everything tracked; the module decides what belongs on it.
  const mine = feed?.filter((entry) => mediaTypesOf(module).includes(entry.mediaType)) ?? []

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

      <ul className="activity-feed">
        {mine.map((activity) => (
          <li key={activity.id} className="activity-row">
            {activity.coverUrl ? (
              <img src={activity.coverUrl} alt="" loading="lazy" />
            ) : (
              <div className="activity-thumb-placeholder" aria-hidden="true" />
            )}
            <div className="activity-text">
              <strong>{activity.title}</strong>
              <span className="muted">{describe(activity)}</span>
            </div>
            <time className="muted" dateTime={activity.createdAt}>
              {relative(activity.createdAt)}
            </time>
          </li>
        ))}
      </ul>
    </AppShell>
  )
}
