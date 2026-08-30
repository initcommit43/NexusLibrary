import { useState } from 'react'
import { api, type TrackedItem } from '../api/client'
import { Heart } from './Heart'

/**
 * Marks a title a favourite, beside the status it is on.
 *
 * <p>Favouriting is a judgement about a title rather than a fact about where it sits on a
 * shelf, so it is its own control next to the status rather than an option inside it — and
 * one press rather than a trip through the editor, which is where it used to live.
 *
 * <p>Written on the press and shown immediately: a heart that fills only once the server
 * answers reads as a heart that did not take.
 */
export const FavouriteToggle = ({
  entry,
  onChanged,
}: {
  entry: TrackedItem
  /** Handed the entry as it came back, so the page around it agrees with the heart. */
  onChanged: (entry: TrackedItem) => void
}) => {
  const [marked, setMarked] = useState(entry.favorite)
  const [failed, setFailed] = useState(false)

  const toggle = async () => {
    const next = !marked
    setMarked(next)
    setFailed(false)
    try {
      onChanged(await api.updateEntry(entry.id, { favorite: next }))
    } catch {
      setMarked(!next)
      setFailed(true)
    }
  }

  return (
    <button
      type="button"
      className={marked ? 'favourite-action on' : 'favourite-action'}
      aria-pressed={marked}
      aria-label={marked ? 'Remove from favourites' : 'Add to favourites'}
      title={failed ? 'That could not be saved. Try again.' : marked ? 'A favourite' : 'Favourite'}
      onClick={() => void toggle()}
    >
      <Heart filled={marked} size={18} />
    </button>
  )
}
