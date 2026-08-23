import { useEffect, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { ApiError, api, type TrackedItem, type TrackingStatus } from '../api/client'
import { AppShell } from '../components/AppShell'
import { StatusPicker } from '../components/StatusPicker'
import { toDisplayScore } from '../components/rating'
import { STATUS_ORDER } from '../components/trackingStatus'
import { moduleBySlug } from '../modules/registry'

export const LibraryPage = () => {
  const { module: slug } = useParams()
  const module = moduleBySlug(slug)

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

  if (!module) {
    return <Navigate to="/" replace />
  }

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

  // One request returns everything tracked; the module decides what belongs on its shelf.
  const mine = entries?.filter((entry) => module.mediaTypes.includes(entry.mediaType)) ?? []
  const byStatus = (status: TrackingStatus) => mine.filter((entry) => entry.status === status)

  return (
    <AppShell module={module}>
      <h1>{module.label}</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries === null && !error && <p className="muted">Loading your library…</p>}

      {entries !== null && mine.length === 0 && (
        <p className="muted">
          {module.emptyHint} <Link to={`/search?module=${module.slug}`}>Search</Link>
        </p>
      )}

      {STATUS_ORDER.map((status) => {
        const group = byStatus(status)
        if (group.length === 0) return null

        return (
          <section key={status} className="status-section">
            <h2>
              {module.statusLabels[status]} <span className="muted">({group.length})</span>
            </h2>

            <div className="cover-grid">
              {group.map((entry) => (
                <article key={entry.id} className="card cover-card">
                  {/* The cover and title are one link: a title-only target is too small
                      to be the obvious way in, and the cover is what people click. The
                      status picker and remove button stay outside it, since nesting
                      controls inside an anchor breaks both clicking and keyboard use. */}
                  <Link className="cover-link" to={`/entries/${entry.id}`}>
                    {entry.coverUrl ? (
                      <img src={entry.coverUrl} alt="" loading="lazy" />
                    ) : (
                      <div className="cover-placeholder" aria-hidden="true" />
                    )}
                    <div className="cover-heading">
                      <h3>{entry.title}</h3>
                      <p className="muted">
                        {entry.releaseDate?.slice(0, 4) ?? 'Unreleased'}
                        {entry.rating !== null && ` · ${toDisplayScore(entry.rating)}`}
                      </p>
                    </div>
                  </Link>
                  <div className="cover-body">
                    <StatusPicker
                      value={entry.status}
                      labels={module.statusLabels}
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
    </AppShell>
  )
}
