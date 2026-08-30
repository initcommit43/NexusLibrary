import { useEffect, useRef, useState } from 'react'
import { ApiError, api, type TrackedItem, type TrackingStatus } from '../api/client'
import { statusLabelsFor, typeDefinitionFor } from '../modules/registry'
import { Heart } from './Heart'
import { STATUS_ORDER } from './trackingStatus'
import { MINUTES_PER_HOUR } from './progress'

/**
 * Everything about one entry, in one place. The card carries no controls of its own: on a
 * shelf of forty, a picker and a delete button on each is more furniture than content, and
 * the things worth editing do not fit there anyway.
 */
export const EntryEditDialog = ({
  entry,
  onSaved,
  onDeleted,
  onClose,
}: {
  entry: TrackedItem
  onSaved: (updated: TrackedItem) => void
  onDeleted: (id: number) => void
  onClose: () => void
}) => {
  const labels = statusLabelsFor(entry.mediaType)
  const tracksMinutes = entry.progressUnit === 'MINUTES'
  const progressLabel = typeDefinitionFor(entry.mediaType)?.progressLabel ?? 'Progress'

  const [status, setStatus] = useState<TrackingStatus>(entry.status)
  const [score, setScore] = useState(entry.rating === null ? '' : (entry.rating / 10).toFixed(1))
  const [progress, setProgress] = useState(
    entry.progressCurrent === null
      ? ''
      : tracksMinutes
        ? (entry.progressCurrent / MINUTES_PER_HOUR).toFixed(1)
        : String(entry.progressCurrent),
  )
  const [startedAt, setStartedAt] = useState(entry.startedAt ?? '')
  const [finishedAt, setFinishedAt] = useState(entry.finishedAt ?? '')
  const [notes, setNotes] = useState(entry.notes ?? '')
  const [favorite, setFavorite] = useState(entry.favorite)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const panel = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  /**
   * Written on the click, unlike every other field here.
   *
   * <p>The heart fills the moment it is pressed, which reads as saved; leaving it to the
   * Save button means closing the dialog quietly drops the mark, and nothing on the way out
   * says so.
   */
  const toggleFavourite = async () => {
    const next = !favorite
    setFavorite(next)
    setError(null)
    try {
      onSaved(await api.updateEntry(entry.id, { favorite: next }))
    } catch (err) {
      setFavorite(!next)
      setError(err instanceof ApiError ? err.message : 'Could not save that.')
    }
  }

  const save = async () => {
    setBusy(true)
    setError(null)
    try {
      const parsedScore = score.trim() === '' ? undefined : Math.round(Number(score) * 10)
      const parsedProgress =
        progress.trim() === ''
          ? undefined
          : tracksMinutes
            ? Math.round(Number(progress) * MINUTES_PER_HOUR)
            : Math.round(Number(progress))

      onSaved(
        await api.updateEntry(entry.id, {
          status,
          ...(parsedScore === undefined ? {} : { rating: parsedScore }),
          ...(parsedProgress === undefined ? {} : { progressCurrent: parsedProgress }),
          ...(startedAt ? { startedAt } : {}),
          ...(finishedAt ? { finishedAt } : {}),
          notes,
          favorite,
        }),
      )
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that.')
      setBusy(false)
    }
  }

  const remove = async () => {
    setBusy(true)
    setError(null)
    try {
      await api.deleteEntry(entry.id)
      onDeleted(entry.id)
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove that.')
      setBusy(false)
    }
  }

  return (
    // Clicking the backdrop closes; clicking inside must not, hence the stopped propagation.
    <div className="dialog-backdrop" onClick={onClose} role="presentation">
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-label={`Edit ${entry.title}`}
        ref={panel}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="dialog-head">
          {entry.coverUrl ? (
            <img className="dialog-cover" src={entry.coverUrl} alt="" />
          ) : (
            <div className="dialog-cover cover-placeholder" aria-hidden="true" />
          )}
          <h2>{entry.title}</h2>

          <button
            type="button"
            className={favorite ? 'ghost icon-button favorite on' : 'ghost icon-button favorite'}
            aria-pressed={favorite}
            aria-label="Favourite"
            onClick={() => void toggleFavourite()}
          >
            <Heart filled={favorite} />
          </button>

          <button type="button" className="ghost icon-button" aria-label="Close" onClick={onClose}>
            ✕
          </button>
        </header>

        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}

        <div className="dialog-row">
          <label className="field">
            <span>Status</span>
            <select value={status} onChange={(e) => setStatus(e.target.value as TrackingStatus)}>
              {STATUS_ORDER.map((option) => (
                <option key={option} value={option}>
                  {labels[option]}
                </option>
              ))}
            </select>
          </label>

          <label className="field">
            <span>{progressLabel}</span>
            <input
              type="number"
              className="number-input"
              min={0}
              step={tracksMinutes ? 0.1 : 1}
              value={progress}
              onChange={(e) => setProgress(e.target.value)}
            />
          </label>

          <label className="field">
            <span>Score</span>
            <input
              type="number"
              className="number-input"
              min={0}
              max={10}
              step={0.5}
              value={score}
              onChange={(e) => setScore(e.target.value)}
            />
          </label>
        </div>

        <div className="dialog-row">
          <label className="field">
            <span>Start date</span>
            <input type="date" value={startedAt} onChange={(e) => setStartedAt(e.target.value)} />
          </label>

          <label className="field">
            <span>Finish date</span>
            <input type="date" value={finishedAt} onChange={(e) => setFinishedAt(e.target.value)} />
          </label>
        </div>

        <label className="field">
          <span>Notes</span>
          <textarea rows={3} value={notes} onChange={(e) => setNotes(e.target.value)} />
        </label>

        <div className="dialog-foot">
          <button type="button" className="ghost danger" disabled={busy} onClick={() => void remove()}>
            Remove from list
          </button>
          <button type="button" disabled={busy} onClick={() => void save()}>
            {busy ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    </div>
  )
}
