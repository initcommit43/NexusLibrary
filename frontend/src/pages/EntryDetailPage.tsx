import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ApiError,
  api,
  type Review,
  type TrackedItem,
  type UpdateEntryPayload,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { RatingInput } from '../components/RatingInput'
import { toDisplayScore } from '../components/rating'
import { StatusPicker } from '../components/StatusPicker'

const asList = (value: unknown) => (Array.isArray(value) ? (value as string[]) : [])

/** Steam reports playtime in minutes, which stops reading naturally after an hour or two. */
const formatProgress = (entry: TrackedItem) => {
  if (entry.progressCurrent === null) return null
  if (entry.progressUnit === 'MINUTES') {
    const hours = entry.progressCurrent / 60
    return `${hours.toFixed(1)} hours played`
  }
  return `${entry.progressCurrent} ${(entry.progressUnit ?? '').toLowerCase()}`
}

export const EntryDetailPage = () => {
  const { id } = useParams()
  const entryId = Number(id)
  const navigate = useNavigate()

  const [entry, setEntry] = useState<TrackedItem | null>(null)
  const [review, setReview] = useState<Review | null>(null)
  const [reviewDraft, setReviewDraft] = useState('')
  const [spoilers, setSpoilers] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const load = useCallback(() => {
    api
      .getEntry(entryId)
      .then(setEntry)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load that entry.'),
      )

    api
      .getReview(entryId)
      .then((found) => {
        setReview(found)
        setReviewDraft(found.body)
        setSpoilers(found.containsSpoilers)
      })
      // A 404 here just means nothing has been written yet.
      .catch(() => setReview(null))
  }, [entryId])

  useEffect(load, [load])

  const save = async (changes: UpdateEntryPayload) => {
    setBusy(true)
    setError(null)
    try {
      setEntry(await api.updateEntry(entryId, changes))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that change.')
    } finally {
      setBusy(false)
    }
  }

  const saveReview = async () => {
    setBusy(true)
    setError(null)
    try {
      const saved = await api.writeReview(entryId, reviewDraft, spoilers)
      setReview(saved)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save your review.')
    } finally {
      setBusy(false)
    }
  }

  const removeReview = async () => {
    setBusy(true)
    try {
      await api.deleteReview(entryId)
      setReview(null)
      setReviewDraft('')
      setSpoilers(false)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete your review.')
    } finally {
      setBusy(false)
    }
  }

  const removeEntry = async () => {
    setBusy(true)
    try {
      await api.deleteEntry(entryId)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove that.')
      setBusy(false)
    }
  }

  if (!entry) {
    return (
      <AppShell>
        {error ? (
          <p className="alert" role="alert">
            {error}
          </p>
        ) : (
          <p className="muted">Loading…</p>
        )}
      </AppShell>
    )
  }

  const platforms = asList(entry.metadata.platforms)
  const genres = asList(entry.metadata.genres)
  const progress = formatProgress(entry)

  return (
    <AppShell>
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      <div className="detail">
        <div className="detail-cover">
          {entry.coverUrl ? (
            <img src={entry.coverUrl} alt="" />
          ) : (
            <div className="cover-placeholder" aria-hidden="true" />
          )}
        </div>

        <div className="detail-body">
          <h1>{entry.title}</h1>
          <p className="muted">
            {entry.releaseDate?.slice(0, 4) ?? 'Unreleased'}
            {genres.length > 0 && ` · ${genres.join(', ')}`}
          </p>

          {platforms.length > 0 && <p className="muted">{platforms.join(' · ')}</p>}

          <div className="detail-controls">
            <label className="field">
              <span>Status</span>
              <StatusPicker
                value={entry.status}
                disabled={busy}
                aria-label="Status"
                onChange={(status) => void save({ status })}
              />
            </label>

            <label className="field">
              <span>Your rating {entry.rating !== null && `— ${toDisplayScore(entry.rating)}`}</span>
              <RatingInput
                value={entry.rating}
                disabled={busy}
                label="Your rating"
                onChange={(rating) => void save({ rating })}
              />
            </label>

            <label className="field">
              <span>Progress {entry.progressUnit === 'MINUTES' && '(minutes)'}</span>
              <input
                type="number"
                min={0}
                defaultValue={entry.progressCurrent ?? ''}
                disabled={busy}
                onBlur={(e) =>
                  e.target.value !== String(entry.progressCurrent ?? '') &&
                  void save({
                    progressCurrent: Number(e.target.value),
                    progressUnit: entry.progressUnit ?? 'MINUTES',
                  })
                }
              />
            </label>
          </div>

          {progress && <p className="muted">{progress}</p>}

          <label className="field">
            <span>Private notes</span>
            <textarea
              rows={3}
              defaultValue={entry.notes ?? ''}
              disabled={busy}
              onBlur={(e) => e.target.value !== (entry.notes ?? '') && void save({ notes: e.target.value })}
            />
          </label>
        </div>
      </div>

      <section className="status-section">
        <h2>Review</h2>
        <div className="card">
          <label className="field">
            <span>{review ? 'Your review' : 'Write a review'}</span>
            <textarea
              rows={6}
              value={reviewDraft}
              disabled={busy}
              placeholder="What did you make of it?"
              onChange={(e) => setReviewDraft(e.target.value)}
            />
          </label>

          <label className="checkbox">
            <input
              type="checkbox"
              checked={spoilers}
              disabled={busy}
              onChange={(e) => setSpoilers(e.target.checked)}
            />
            <span>Contains spoilers</span>
          </label>

          <div className="detail-actions">
            <button type="button" disabled={busy || !reviewDraft.trim()} onClick={() => void saveReview()}>
              {review ? 'Update review' : 'Save review'}
            </button>
            {review && (
              <button type="button" className="ghost" disabled={busy} onClick={() => void removeReview()}>
                Delete review
              </button>
            )}
          </div>
        </div>
      </section>

      <section className="status-section">
        <button type="button" className="ghost small" disabled={busy} onClick={() => void removeEntry()}>
          Remove from library
        </button>
      </section>
    </AppShell>
  )
}
