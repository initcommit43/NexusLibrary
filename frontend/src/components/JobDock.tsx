import { useEffect, useState, type CSSProperties } from 'react'
import type { SyncJob } from '../api/client'
import { cancelJob, resumeRunningJob, useWatchedJob } from '../api/jobs'
import { useAuth } from '../auth/useAuth'
import { MODULES } from '../modules/registry'

const KIND_NOUNS: Record<SyncJob['kind'], string> = {
  IMPORT: 'import',
  ACHIEVEMENTS: 'achievement sync',
  ACTIVITY: 'activity import',
  NOTIFICATIONS: 'notification import',
}

const PHASE_LABELS: Record<NonNullable<SyncJob['phase']>, string> = {
  FETCHING: 'Fetching',
  MATCHING: 'Matching',
  IMPORTING: 'Importing',
}

const OUTCOMES: Record<SyncJob['state'], string> = {
  RUNNING: 'running',
  COMPLETE: 'finished',
  FAILED: 'failed',
  CANCELLED: 'stopped',
}

/** "Steam import", under the provider's name as its settings card gives it. */
const nameOf = (job: SyncJob): string => {
  const provider = MODULES.flatMap((module) => module.providers).find(
    (candidate) => candidate.provider === job.provider,
  )
  const noun = KIND_NOUNS[job.kind] ?? 'sync'
  return provider ? `${provider.label} ${noun}` : noun
}

const capitalised = (text: string) => text.charAt(0).toUpperCase() + text.slice(1)

/**
 * A run in progress, carried to whichever page the reader has moved on to.
 *
 * <p>There only while something runs: a finished import has already said how it went on the
 * settings card, and a dock that lingered would be one more thing to dismiss.
 */
export const JobDock = () => {
  const { user } = useAuth()
  const { job, cancelling } = useWatchedJob()

  useEffect(() => {
    if (user) resumeRunningJob(user.id)
  }, [user])

  /*
   * The live region speaks on changes of stage — started, next phase, ended — and never on the
   * count, which moves every second. The stage this dock mounted on is held back, so moving
   * between pages does not announce a run the reader has already heard about.
   */
  const stage = job ? `${job.id}:${job.state}:${job.phase ?? ''}` : ''
  const [mountedOn] = useState(stage)
  let announcement = ''
  if (job && stage !== mountedOn) {
    const name = capitalised(nameOf(job))
    const phase = job.phase ? `: ${PHASE_LABELS[job.phase].toLowerCase()}` : ''
    announcement =
      job.state === 'RUNNING' ? `${name} running${phase}` : `${name} ${OUTCOMES[job.state]}`
  }

  const running = job?.state === 'RUNNING' ? job : null

  return (
    <div className="job-dock">
      <p className="job-dock-announce" aria-live="polite" aria-atomic="true">
        {announcement}
      </p>

      {running && <JobPanel job={running} cancelling={cancelling === running.id} />}
    </div>
  )
}

const JobPanel = ({ job, cancelling }: { job: SyncJob; cancelling: boolean }) => {
  const name = nameOf(job)
  const title = capitalised(name)
  const phase = job.phase ? PHASE_LABELS[job.phase] : null
  // A history walk has no length until it reaches the end, and a phase just begun has none yet.
  const known = job.total > 0
  const count = known ? `${job.processed} of ${job.total}` : job.processed > 0 ? `${job.processed}` : ''
  const detail = [phase, count].filter(Boolean).join(' · ')

  return (
    <section className="job-dock-panel" aria-label={title}>
      <div className="job-dock-head">
        <div className="job-dock-text">
          <span className="job-dock-title">{title}</span>
          <span className="job-dock-detail">{detail || 'Starting…'}</span>
        </div>

        {/* Held back with aria-disabled rather than disabled, so focus is not dropped on the press. */}
        <button
          type="button"
          className="ghost small"
          aria-disabled={cancelling}
          aria-label={cancelling ? `Stopping the ${name}` : `Cancel the ${name}`}
          onClick={() => {
            if (!cancelling) void cancelJob(job.id)
          }}
        >
          {cancelling ? 'Stopping…' : 'Cancel'}
        </button>
      </div>

      {/* Without a length the bar travels rather than fills, and claims no value to read out. */}
      <div
        className="job-dock-bar"
        role="progressbar"
        aria-label={`${title} progress`}
        aria-valuemin={known ? 0 : undefined}
        aria-valuemax={known ? job.total : undefined}
        aria-valuenow={known ? job.processed : undefined}
        aria-valuetext={known ? detail : undefined}
      >
        <div
          className={known ? 'job-dock-fill' : 'job-dock-fill is-running'}
          style={
            known
              ? ({ '--progress': Math.min(1, job.processed / job.total) } as CSSProperties)
              : undefined
          }
        />
      </div>
    </section>
  )
}
