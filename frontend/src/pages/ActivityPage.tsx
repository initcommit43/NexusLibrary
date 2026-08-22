import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api, type ActivityEntry } from '../api/client'
import { AppShell } from '../components/AppShell'
import { toDisplayScore } from '../components/rating'
import { STATUS_LABELS } from '../components/trackingStatus'
import type { TrackingStatus } from '../api/client'

const statusLabel = (raw?: string | null) =>
  raw && raw in STATUS_LABELS ? STATUS_LABELS[raw as TrackingStatus] : raw

/** Reads the stored old-to-new payload back as a sentence. */
const describe = (activity: ActivityEntry): string => {
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

  return (
    <AppShell>
      <h1>Activity</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {feed === null && !error && <p className="muted">Loading…</p>}

      {feed?.length === 0 && (
        <p className="muted">
          Nothing yet. <Link to="/search">Track a game</Link> and your history shows up here.
        </p>
      )}

      <ul className="activity-feed">
        {feed?.map((activity) => (
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
