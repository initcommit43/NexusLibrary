import { useEffect, useState } from 'react'
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom'
import { ApiError, api, type MediaType, type TrackedItem, type TrackingStatus } from '../api/client'
import { AppShell } from '../components/AppShell'
import { StatusPicker } from '../components/StatusPicker'
import { toDisplayScore } from '../components/rating'
import { STATUS_ORDER } from '../components/trackingStatus'
import { moduleBySlug, statusLabelsFor } from '../modules/registry'

export const LibraryPage = () => {
  const { module: slug } = useParams()
  const [params, setParams] = useSearchParams()
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

  // A module with two kinds of thing shows one at a time: anime and manga share a shelf in
  // no other app either, and mixing them would mean picking one set of words for both.
  const requested = params.get('type') as MediaType | null
  const active =
    module.types.find((type) => type.mediaType === requested) ??
    module.types.find((type) => type.mediaType === module.defaultMediaType) ??
    module.types[0]

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

  const mine = entries?.filter((entry) => entry.mediaType === active.mediaType) ?? []
  const byStatus = (status: TrackingStatus) => mine.filter((entry) => entry.status === status)

  return (
    <AppShell module={module}>
      <h1>{module.label}</h1>

      {module.types.length > 1 && (
        <div className="type-tabs" role="tablist" aria-label={`${module.label} kinds`}>
          {module.types.map((type) => (
            <button
              key={type.mediaType}
              type="button"
              role="tab"
              aria-selected={type.mediaType === active.mediaType}
              className={type.mediaType === active.mediaType ? 'type-tab active' : 'type-tab'}
              onClick={() => setParams({ type: type.mediaType })}
            >
              {type.label}
            </button>
          ))}
        </div>
      )}

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries === null && !error && <p className="muted">Loading your library…</p>}

      {entries !== null && mine.length === 0 && (
        <p className="muted">
          {module.emptyHint}{' '}
          <Link to={`/search?module=${module.slug}&type=${active.mediaType}`}>
            {active.searchPlaceholder}
          </Link>
        </p>
      )}

      {STATUS_ORDER.map((status) => {
        const group = byStatus(status)
        if (group.length === 0) return null

        return (
          <section key={status} className="status-section">
            <h2>
              {statusLabelsFor(active.mediaType)[status]}{' '}
              <span className="muted">({group.length})</span>
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
                      mediaType={entry.mediaType}
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
