import { useEffect, useState } from 'react'
import { ApiError, api, type Review, type TrackedItem } from '../api/client'

/**
 * What this reader made of the title, written on the title's own page.
 *
 * <p>Only from a shelf other than planning. Planning to read something is not an opinion of
 * it, and the server refuses the write either way — what this adds is saying so before the
 * box is reached for, rather than after the writing is done.
 *
 * <p>Dropped and paused both count: someone who gave up halfway has as much to say as someone
 * who finished, and often more.
 */
export const MediaReview = ({ entry }: { entry: TrackedItem | null }) => {
  const [review, setReview] = useState<Review | null>(null)
  const [draft, setDraft] = useState('')
  const [spoilers, setSpoilers] = useState(false)
  const [editing, setEditing] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const entryId = entry?.id ?? null
  const started = entry !== null && entry.status !== 'PLANNING'

  useEffect(() => {
    if (entryId === null || !started) return

    let current = true
    api
      .getReview(entryId)
      .then((found) => {
        if (!current) return
        setReview(found)
        setDraft(found.body)
        setSpoilers(found.containsSpoilers)
      })
      // A 404 here just means nothing has been written yet.
      .catch(() => current && setReview(null))

    return () => {
      current = false
    }
  }, [entryId, started])

  const save = async () => {
    if (entryId === null) return
    setBusy(true)
    setError(null)
    try {
      setReview(await api.writeReview(entryId, draft, spoilers))
      setEditing(false)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that review.')
    } finally {
      setBusy(false)
    }
  }

  const remove = async () => {
    if (entryId === null) return
    setBusy(true)
    setError(null)
    try {
      await api.deleteReview(entryId)
      setReview(null)
      setDraft('')
      setSpoilers(false)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not delete that review.')
    } finally {
      setBusy(false)
    }
  }

  // Said rather than hidden: an absent panel reads as a page that has no reviews, and leaves
  // the reader to guess what would bring it back.
  if (!started) {
    return (
      <section className="status-section">
        <h2>Review</h2>
        <p className="muted">
          {entry === null
            ? 'Add this to your list and start it to write a review.'
            : 'Move this off the planning shelf to write a review.'}
        </p>
      </section>
    )
  }

  return (
    <section className="status-section">
      <h2>Review</h2>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {/* A saved review reads as text. Leaving it in a textarea makes finished writing
          look like an unsaved draft, so the editor only appears while editing. */}
      {review && !editing ? (
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
                setDraft(review.body)
                setSpoilers(review.containsSpoilers)
                setEditing(true)
              }}
            >
              Edit
            </button>
            <button type="button" className="ghost small" disabled={busy} onClick={() => void remove()}>
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
              value={draft}
              disabled={busy}
              placeholder="What did you make of it?"
              onChange={(e) => setDraft(e.target.value)}
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
            <button type="button" disabled={busy || !draft.trim()} onClick={() => void save()}>
              {review ? 'Save changes' : 'Save review'}
            </button>
            {review && (
              <button
                type="button"
                className="ghost"
                disabled={busy}
                onClick={() => {
                  setDraft(review.body)
                  setSpoilers(review.containsSpoilers)
                  setEditing(false)
                }}
              >
                Cancel
              </button>
            )}
          </div>
        </div>
      )}
    </section>
  )
}
