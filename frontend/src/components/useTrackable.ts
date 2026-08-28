import { useEffect, useState } from 'react'
import { ApiError, api, type SearchResult, type TrackedItem, type TrackingStatus } from '../api/client'

type TrackState = Record<string, 'idle' | 'saving' | 'tracked'>

export const keyOf = (result: Pick<SearchResult, 'source' | 'externalId'>) =>
  `${result.source}:${result.externalId}`

export interface Trackable {
  /** What the button on a given card should currently say. */
  stateOf: (result: SearchResult) => 'idle' | 'saving' | 'tracked'
  track: (result: SearchResult, status: TrackingStatus) => Promise<void>
  /** Opens the editor over a result, putting it on a shelf first if it is not on one. */
  edit: (result: SearchResult) => Promise<void>
  /** The entry the editor is open over, if any. Pages render the dialog themselves. */
  editing: TrackedItem | null
  closeEditor: () => void
  saved: (updated: TrackedItem) => void
  deleted: (id: number) => void
  /** Set when the last track attempt failed; the page decides where to show it. */
  error: string | null
  clearError: () => void
}

/**
 * Adding a catalogue result to a shelf and editing it once it is on one, shared by the pages
 * that show catalogue results.
 *
 * <p>The reader's own entries are loaded once and kept in step locally rather than re-read
 * after every change: a browse page shows four shelves at once, and re-fetching the whole
 * library per card would cost more than the change did.
 */
export const useTrackable = (): Trackable => {
  const [trackState, setTrackState] = useState<TrackState>({})
  const [entries, setEntries] = useState<Record<string, TrackedItem>>({})
  const [editing, setEditing] = useState<TrackedItem | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .listEntries()
      .then((list) => setEntries(Object.fromEntries(list.map((entry) => [keyOf(entry), entry]))))
      .catch(() => {
        // A failure here only costs the "already tracked" hint, so the page still works.
      })
  }, [])

  const remember = (entry: TrackedItem) => {
    setEntries((held) => ({ ...held, [keyOf(entry)]: entry }))
    return entry
  }

  const stateOf = (result: SearchResult) =>
    trackState[keyOf(result)] ?? (entries[keyOf(result)] ? 'tracked' : 'idle')

  const put = async (result: SearchResult, status: TrackingStatus): Promise<TrackedItem | null> => {
    const key = keyOf(result)
    const held = entries[key]

    setTrackState((s) => ({ ...s, [key]: 'saving' }))
    setError(null)

    try {
      // Already on a shelf: this is a move between shelves, not a second copy of it.
      const entry = held
        ? await api.updateEntry(held.id, { status })
        : await api.createEntry({ source: result.source, externalId: result.externalId, status })

      setTrackState((s) => ({ ...s, [key]: 'tracked' }))
      return remember(entry)
    } catch (err) {
      setTrackState((s) => ({ ...s, [key]: held ? 'tracked' : 'idle' }))
      setError(err instanceof ApiError ? err.message : 'Could not save that. Please try again.')
      return null
    }
  }

  const track = async (result: SearchResult, status: TrackingStatus) => {
    await put(result, status)
  }

  /**
   * The editor edits an entry, so a title that is not on a shelf goes onto one first. Planning
   * is the neutral shelf to arrive on, and the dialog that opens can change or remove it
   * without a second trip.
   */
  const edit = async (result: SearchResult) => {
    const held = entries[keyOf(result)]
    if (held) {
      setEditing(held)
      return
    }

    const created = await put(result, 'PLANNING')
    if (created) setEditing(created)
  }

  const deleted = (id: number) => {
    setEntries((held) =>
      Object.fromEntries(Object.entries(held).filter(([, entry]) => entry.id !== id)),
    )
    setTrackState((s) => {
      const next = { ...s }
      for (const [key, entry] of Object.entries(entries)) {
        if (entry.id === id) delete next[key]
      }
      return next
    })
    setEditing(null)
  }

  return {
    stateOf,
    track,
    edit,
    editing,
    closeEditor: () => setEditing(null),
    saved: (updated) => {
      remember(updated)
      setEditing(null)
    },
    deleted,
    error,
    clearError: () => setError(null),
  }
}
