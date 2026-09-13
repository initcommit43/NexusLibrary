import { useSyncExternalStore } from 'react'
import { ApiError, api, type SyncJob } from './client'

/**
 * The background run this reader is watching, held once for the whole app.
 *
 * <p>Settings starts a run and shows its outcome; the dock in the shell carries it to every
 * other page and can call it off. Each polling on its own would let them disagree — the dock
 * cancels while settings goes on counting — so both read this, and only this polls.
 *
 * <p>Nothing polls while nothing runs. The server is asked once per signed-in reader whether
 * something is already going, which covers a reload mid-import; a run started from another
 * tab or device is only noticed on the next load.
 */

const POLL_MS = 1000
/** A dropped poll or two is the network, not the run; more than that and we have lost it. */
const MISSES_ALLOWED = 3

export type WatchedJob = {
  /** The latest state seen, kept after it finishes so settings can say how it went. */
  job: SyncJob | null
  /** Which run has been asked to stop: it carries on until it reaches its next item. */
  cancelling: string | null
}

let snapshot: WatchedJob = { job: null, cancelling: null }
let watching: { id: string; done: Promise<SyncJob | null> } | null = null
/** Whose run this is, so signing in as someone else never shows the last reader's import. */
let owner: number | null = null
let checked = false

const listeners = new Set<() => void>()

const publish = (next: Partial<WatchedJob>) => {
  snapshot = { ...snapshot, ...next }
  listeners.forEach((listener) => listener())
}

const pause = () => new Promise((resolve) => setTimeout(resolve, POLL_MS))

const follow = async (jobId: string): Promise<SyncJob | null> => {
  let misses = 0

  for (;;) {
    let current: SyncJob
    try {
      current = await api.syncJob(jobId)
      misses = 0
    } catch (err) {
      if (watching?.id !== jobId) return null
      misses += 1
      // Gone means gone: the registry forgets finished runs and a restart forgets all of them.
      if ((err instanceof ApiError && err.status === 404) || misses >= MISSES_ALLOWED) {
        watching = null
        checked = false
        publish({ job: null, cancelling: null })
        return null
      }
      await pause()
      continue
    }

    // Someone else's watch took over while this answer was on its way.
    if (watching?.id !== jobId) return null

    if (current.state !== 'RUNNING' && current.followUpJobId) {
      // Handed straight over without publishing the end, or the dock would blink out and back
      // between an import and the achievements that follow it.
      void watchJob(current.followUpJobId)
      return current
    }

    publish({
      job: current,
      cancelling: current.state === 'RUNNING' ? snapshot.cancelling : null,
    })

    if (current.state !== 'RUNNING') {
      watching = null
      return current
    }
    await pause()
  }
}

/**
 * Watches a run to its end. Called by whatever started it; a second call for the same run
 * shares the first one's poll rather than starting another.
 *
 * @return the finished run, or null if sight of it was lost
 */
export const watchJob = (jobId: string): Promise<SyncJob | null> => {
  if (watching?.id === jobId) return watching.done
  watching = { id: jobId, done: Promise.resolve(null) }
  watching.done = follow(jobId)
  return watching.done
}

/** Clears an outcome before a new run starts, so the last one's result is not read as its. */
export const forgetFinishedJob = () => {
  if (snapshot.job && snapshot.job.state !== 'RUNNING') publish({ job: null })
}

/** Asks the server to stop a run; the poll reports when it actually has. */
export const cancelJob = async (jobId: string) => {
  publish({ cancelling: jobId })
  try {
    await api.cancelJob(jobId)
  } catch {
    // Still running, so the button comes back rather than claiming a stop that never happened.
    if (snapshot.cancelling === jobId) publish({ cancelling: null })
  }
}

/** Picks up a run already going for this reader — after a reload, say — once per sign-in. */
export const resumeRunningJob = (userId: number) => {
  if (owner !== userId) {
    owner = userId
    checked = false
    watching = null
    publish({ job: null, cancelling: null })
  }
  if (checked || watching) return
  checked = true

  api
    .currentJob()
    .then((found) => {
      if (owner !== userId || watching || found?.state !== 'RUNNING') return
      void watchJob(found.id)
    })
    .catch(() => {
      if (owner === userId) checked = false
    })
}

const subscribe = (listener: () => void) => {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export const useWatchedJob = (): WatchedJob => useSyncExternalStore(subscribe, () => snapshot)
