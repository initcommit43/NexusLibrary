import { useEffect, useState } from 'react'
import { ApiError, api, type SearchResult, type TrackingStatus } from '../api/client'

type TrackState = Record<string, 'idle' | 'saving' | 'tracked'>

export const keyOf = (result: Pick<SearchResult, 'source' | 'externalId'>) =>
  `${result.source}:${result.externalId}`

export interface Trackable {
  /** What the button on a given card should currently say. */
  stateOf: (result: SearchResult) => 'idle' | 'saving' | 'tracked'
  track: (result: SearchResult, status: TrackingStatus) => Promise<void>
  /** Set when the last track attempt failed; the page decides where to show it. */
  error: string | null
  clearError: () => void
}

/**
 * Adding a catalogue result to a shelf, shared by the pages that show catalogue results.
 *
 * <p>The already-tracked set is loaded once and kept in sync locally rather than re-read
 * after every add: a browse page shows four shelves at once, and re-fetching the whole
 * library per card would cost more than the add did.
 */
export const useTrackable = (): Trackable => {
  const [trackState, setTrackState] = useState<TrackState>({})
  const [alreadyTracked, setAlreadyTracked] = useState<Set<string>>(new Set())
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .listEntries()
      .then((entries) => setAlreadyTracked(new Set(entries.map(keyOf))))
      .catch(() => {
        // A failure here only costs the "already tracked" hint, so the page still works.
      })
  }, [])

  const stateOf = (result: SearchResult) =>
    trackState[keyOf(result)] ?? (alreadyTracked.has(keyOf(result)) ? 'tracked' : 'idle')

  const track = async (result: SearchResult, status: TrackingStatus) => {
    const key = keyOf(result)
    setTrackState((s) => ({ ...s, [key]: 'saving' }))
    setError(null)

    try {
      await api.createEntry({ source: result.source, externalId: result.externalId, status })
      setTrackState((s) => ({ ...s, [key]: 'tracked' }))
      setAlreadyTracked((tracked) => new Set(tracked).add(key))
    } catch (err) {
      setTrackState((s) => ({ ...s, [key]: 'idle' }))
      setError(err instanceof ApiError ? err.message : 'Could not save that. Please try again.')
    }
  }

  return { stateOf, track, error, clearError: () => setError(null) }
}
