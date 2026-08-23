import { useCallback, useEffect, useState } from 'react'
import { api, type SyncJob } from '../api/client'

const PROVIDER_LABELS: Record<string, string> = {
  STEAM: 'Steam',
  ANILIST: 'AniList',
  MAL: 'MyAnimeList',
  TRAKT: 'Trakt',
}

const describe = (job: SyncJob): string => {
  const who = PROVIDER_LABELS[job.provider ?? ''] ?? 'library'
  if (job.kind === 'ACHIEVEMENTS') {
    return `${who} achievements — ${job.processed} of ${job.total}`
  }
  return job.total > 0
    ? `Importing ${who} — ${job.processed} of ${job.total}`
    : `Fetching your ${who} list…`
}

/**
 * Follows a running import around the app.
 *
 * <p>An import is minutes of work, and settings is not somewhere anyone wants to sit and
 * watch. It reports from the corner instead, and carries the button to call it off — the
 * alternative to waiting being to wait.
 */
export const ImportIndicator = () => {
  const [job, setJob] = useState<SyncJob | null>(null)
  const [dismissed, setDismissed] = useState(false)

  const poll = useCallback(async () => {
    try {
      const current = await api.currentJob()
      setJob(current)
      if (current) setDismissed(false)
    } catch {
      // A failed poll says nothing about the run; the next one will.
    }
  }, [])

  useEffect(() => {
    const first = setTimeout(() => void poll(), 0)
    const timer = setInterval(() => void poll(), 1500)
    return () => {
      clearTimeout(first)
      clearInterval(timer)
    }
  }, [poll])

  if (!job || dismissed) return null

  const percent = job.total > 0 ? Math.round((job.processed / job.total) * 100) : 0

  const cancel = async () => {
    try {
      await api.cancelJob(job.id)
      setDismissed(true)
    } catch {
      // If it will not stop, the indicator keeps reporting rather than lying about it.
    }
  }

  return (
    <aside className="import-indicator" aria-live="polite">
      <div className="import-indicator-text">
        <span>{describe(job)}</span>
        {job.total > 0 && (
          <div className="achievement-bar">
            <div className="achievement-bar-fill" style={{ width: `${percent}%` }} />
          </div>
        )}
      </div>

      <button
        type="button"
        className="ghost icon-button"
        aria-label="Stop this import"
        title="Stop this import"
        onClick={() => void cancel()}
      >
        ✕
      </button>
    </aside>
  )
}
