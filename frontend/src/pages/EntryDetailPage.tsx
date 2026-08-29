import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  ApiError,
  api,
  type Review,
  type TrackedItem,
  type UpdateEntryPayload,
} from '../api/client'
import { AchievementList } from '../components/AchievementList'
import { achievementCatalogue, achievementProgress } from '../components/achievements'
import { AppShell } from '../components/AppShell'
import { moduleForMediaType } from '../modules/registry'
import { RatingInput } from '../components/RatingInput'
import { hoursToMinutes, progressFieldValue, progressLabel } from '../components/progress'
import { toDisplayScore } from '../components/rating'
import { StatusPicker } from '../components/StatusPicker'

const asList = (value: unknown) => (Array.isArray(value) ? (value as string[]) : [])

export const EntryDetailPage = () => {
  const { id } = useParams()
  const entryId = Number(id)
  const navigate = useNavigate()

  const [entry, setEntry] = useState<TrackedItem | null>(null)
  const [review, setReview] = useState<Review | null>(null)
  const [reviewDraft, setReviewDraft] = useState('')
  const [spoilers, setSpoilers] = useState(false)
  const [editingReview, setEditingReview] = useState(false)
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
      setEditingReview(false)
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
      setEditingReview(false)
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
  const tracksMinutes = entry.progressUnit === 'MINUTES' || entry.progressUnit === null

  return (
    <AppShell module={moduleForMediaType(entry.mediaType)}>
      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      <div className="entry-top">
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
                mediaType={entry.mediaType}
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
              <span>{progressLabel(entry)}</span>
              <input
                className="number-input"
                type="number"
                inputMode="decimal"
                min={0}
                step={tracksMinutes ? 0.1 : 1}
                // Keyed so the field picks up a new saved value instead of holding the
                // stale one it was first mounted with.
                key={entry.progressCurrent ?? 'empty'}
                defaultValue={progressFieldValue(entry)}
                disabled={busy}
                onBlur={(e) => {
                  if (e.target.value === progressFieldValue(entry)) return
                  const typed = Number(e.target.value)
                  void save({
                    progressCurrent: tracksMinutes ? hoursToMinutes(typed) : Math.round(typed),
                    progressUnit: entry.progressUnit ?? 'MINUTES',
                  })
                }}
              />
            </label>
          </div>

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

      </div>

      <AchievementList
        catalogue={achievementCatalogue(entry)}
        progress={achievementProgress(entry)}
      />

      <section className="status-section">
        <h2>Review</h2>

        {/* A saved review reads as text. Leaving it in a textarea makes finished writing
            look like an unsaved draft, so the editor only appears while editing. */}
        {review && !editingReview ? (
          <div className="card review-card">
            {review.containsSpoilers && <p className="spoiler-flag">Contains spoilers</p>}
            <p className="review-body">{review.body}</p>
            <p className="muted">
              {review.updatedAt !== review.createdAt ? 'Edited ' : 'Written '}
              {new Date(review.updatedAt).toLocaleDateString()}
            </p>
            <div className="detail-actions">
              <button
                type="button"
                className="ghost small"
                disabled={busy}
                onClick={() => {
                  setReviewDraft(review.body)
                  setSpoilers(review.containsSpoilers)
                  setEditingReview(true)
                }}
              >
                Edit
              </button>
              <button type="button" className="ghost small" disabled={busy} onClick={() => void removeReview()}>
                Delete
              </button>
            </div>
          </div>
        ) : (
          <div className="card">
            <label className="field">
              <span>{review ? 'Edit your review' : 'Write a review'}</span>
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
                {review ? 'Save changes' : 'Save review'}
              </button>
              {review && (
                <button
                  type="button"
                  className="ghost"
                  disabled={busy}
                  onClick={() => {
                    setReviewDraft(review.body)
                    setSpoilers(review.containsSpoilers)
                    setEditingReview(false)
                  }}
                >
                  Cancel
                </button>
              )}
            </div>
          </div>
        )}
      </section>

      <section className="status-section">
        <button type="button" className="ghost small" disabled={busy} onClick={() => void removeEntry()}>
          Remove from library
        </button>
      </section>
    </AppShell>
  )
}
