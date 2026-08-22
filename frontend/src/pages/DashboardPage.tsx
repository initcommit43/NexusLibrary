import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api, type TrackedItem, type TrackingStatus } from '../api/client'
import { AppShell } from '../components/AppShell'
import { StatusPicker } from '../components/StatusPicker'
import { toDisplayScore } from '../components/rating'
import { STATUS_LABELS, STATUS_ORDER } from '../components/trackingStatus'

const PENDING_MODULES = [
  { label: 'Movies & TV', phase: 'Phase 6' },
  { label: 'Anime & Manga', phase: 'Phase 5' },
  { label: 'Books', phase: 'Phase 7' },
]

export const DashboardPage = () => {
  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)

  useEffect(() => {
    api
      .listEntries()
      .then(setEntries)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your library.'),
      )
  }, [])

  const changeStatus = async (entry: TrackedItem, status: TrackingStatus) => {
    setBusyId(entry.id)
    setError(null)
    try {
      const updated = await api.updateEntry(entry.id, { status })
      setEntries((current) => current?.map((e) => (e.id === updated.id ? updated : e)) ?? null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that change.')
    } finally {
      setBusyId(null)
    }
  }

  const remove = async (entry: TrackedItem) => {
    setBusyId(entry.id)
    setError(null)
    try {
      await api.deleteEntry(entry.id)
      setEntries((current) => current?.filter((e) => e.id !== entry.id) ?? null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove that.')
    } finally {
      setBusyId(null)
    }
  }

  const byStatus = (status: TrackingStatus) => entries?.filter((e) => e.status === status) ?? []

  return (
    <AppShell>
      <h1>Games</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries === null && !error && <p className="muted">Loading your library…</p>}

      {entries?.length === 0 && (
        <p className="muted">
          Nothing tracked yet. <Link to="/search">Find a game</Link> to get started.
        </p>
      )}

      {STATUS_ORDER.map((status) => {
        const group = byStatus(status)
        if (group.length === 0) return null

        return (
          <section key={status} className="status-section">
            <h2>
              {STATUS_LABELS[status]} <span className="muted">({group.length})</span>
            </h2>

            <div className="cover-grid">
              {group.map((entry) => (
                <article key={entry.id} className="card cover-card">
                  {entry.coverUrl ? (
                    <img src={entry.coverUrl} alt="" loading="lazy" />
                  ) : (
                    <div className="cover-placeholder" aria-hidden="true" />
                  )}
                  <div className="cover-body">
                    <h3>
                      <Link to={`/entries/${entry.id}`}>{entry.title}</Link>
                    </h3>
                    <p className="muted">
                      {entry.releaseDate?.slice(0, 4) ?? 'Unreleased'}
                      {entry.rating !== null && ` · ${toDisplayScore(entry.rating)}`}
                    </p>
                    <StatusPicker
                      value={entry.status}
                      disabled={busyId === entry.id}
                      aria-label={`Status for ${entry.title}`}
                      onChange={(next) => void changeStatus(entry, next)}
                    />
                    <button
                      type="button"
                      className="ghost small"
                      disabled={busyId === entry.id}
                      onClick={() => void remove(entry)}
                    >
                      Remove
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </section>
        )
      })}

      <section className="status-section">
        <h2 className="muted">Other modules</h2>
        <div className="module-grid">
          {PENDING_MODULES.map((module) => (
            <article key={module.label} className="card module-card">
              <h3>{module.label}</h3>
              <p className="muted">Not built yet — {module.phase}.</p>
            </article>
          ))}
        </div>
      </section>
    </AppShell>
  )
}
