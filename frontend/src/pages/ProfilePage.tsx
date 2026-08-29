import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api, type MediaType, type TrackedItem } from '../api/client'
import { AppShell } from '../components/AppShell'
import { FavouriteGrid } from '../components/FavouriteGrid'
import { FavouriteRows } from '../components/FavouriteRows'
import { Grip } from '../components/Grip'
import { useAuth } from '../auth/useAuth'
import { MODULES } from '../modules/registry'

/** Ratings are stored 0–100 and shown out of ten, the way every entry shows its own. */
const RATING_SCALE = 10

/** Arranged first, in the order they were dragged into; then everything never moved. */
const byRank = (a: TrackedItem, b: TrackedItem): number => {
  if (a.favoriteRank === null) return b.favoriteRank === null ? 0 : 1
  if (b.favoriteRank === null) return -1
  return a.favoriteRank - b.favoriteRank
}

/** Rows the reader placed, in that order; the rest keep the order the app lists them in. */
const byPlacement = (order: MediaType[]) => (a: MediaType, b: MediaType) => {
  const placed = (type: MediaType) => {
    const at = order.indexOf(type)
    return at === -1 ? Number.MAX_SAFE_INTEGER : at
  }
  return placed(a) - placed(b)
}

const Stat = ({ label, value, hint }: { label: string; value: number; hint?: string }) => (
  <li className="profile-stat">
    <b>{value}</b>
    <span>{label}</span>
    {hint && <span className="muted">{hint}</span>}
  </li>
)

export const ProfilePage = () => {
  const { user } = useAuth()
  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [rowOrder, setRowOrder] = useState<MediaType[]>([])
  const [arranging, setArranging] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.listEntries(), api.favouriteRowOrder()])
      .then(([library, order]) => {
        setEntries(library)
        setRowOrder(order)
      })
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your library.'),
      )
  }, [])

  const totals = useMemo(() => {
    const all = entries ?? []
    const rated = all.filter((entry) => entry.rating !== null)
    const sum = rated.reduce((running, entry) => running + (entry.rating ?? 0), 0)

    return {
      tracked: all.length,
      completed: all.filter((entry) => entry.status === 'COMPLETED').length,
      favorites: all.filter((entry) => entry.favorite).length,
      rated: rated.length,
      average: rated.length ? sum / rated.length / RATING_SCALE : null,
    }
  }, [entries])

  /*
   * One grid per kind of thing, in the order the app lists them, and only the kinds that
   * have any. Not per module: a module can hold two of them, and anime beside manga is a
   * shelf of two different habits — as films are beside the series you watch weekly.
   *
   * A heading over an empty grid says the kind exists; here it would only say you have
   * nothing in it, which the shelf counts below already say better.
   */
  const favourites = useMemo(() => {
    const marked = (entries ?? []).filter((entry) => entry.favorite)

    return MODULES.flatMap((module) => module.types)
      .sort((a, b) => byPlacement(rowOrder)(a.mediaType, b.mediaType))
      .map((type) => ({
        key: type.mediaType,
        label: type.label,
        marked: marked.filter((entry) => entry.mediaType === type.mediaType).sort(byRank),
      }))
      .filter((group) => group.marked.length > 0)
  }, [entries, rowOrder])

  /**
   * Writes the order of the rows, the ones with nothing in them included.
   *
   * <p>An empty row is not drawn but keeps its place: it is put back at the index it was
   * stored at, so unfavouriting the last title in a row and marking another later brings the
   * row back where it was rather than at the end.
   */
  const reorderRows = async (ordered: MediaType[]) => {
    const hidden = rowOrder.filter((type) => !ordered.includes(type))
    const next = [...ordered]
    for (const type of hidden) {
      next.splice(Math.min(rowOrder.indexOf(type), next.length), 0, type)
    }

    const held = rowOrder
    setRowOrder(next)
    try {
      setRowOrder(await api.replaceFavouriteRowOrder(next))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that order.')
      setRowOrder(held)
    }
  }

  // Every shelf the app has, including the empty ones: a count of zero is the honest answer
  // and the link is how you go and change it.
  const shelves = useMemo(
    () =>
      MODULES.flatMap((module) =>
        module.types.map((type) => ({
          module,
          type,
          count: (entries ?? []).filter((entry) => entry.mediaType === type.mediaType).length,
        })),
      ),
    [entries],
  )

  /**
   * Written as one arrangement of every favourite, not of the module that was dragged.
   *
   * <p>Rank is a position in that whole list, so sending only the grid that moved would
   * hand two of them the same numbers. The grid shows the new order before the request
   * answers; if the write fails the server's own copy is what comes back.
   */
  const reorder = async (key: string, ordered: TrackedItem[]) => {
    const arranged = favourites.flatMap((group) => (group.key === key ? ordered : group.marked))
    const ranks = new Map(arranged.map((entry, rank) => [entry.id, rank]))

    setEntries(
      (held) =>
        held?.map((entry) => {
          const rank = ranks.get(entry.id)
          return rank === undefined ? entry : { ...entry, favoriteRank: rank }
        }) ?? null,
    )

    try {
      const saved = await api.reorderFavourites(arranged.map((entry) => entry.id))
      const byId = new Map(saved.map((entry) => [entry.id, entry]))
      setEntries((held) => held?.map((entry) => byId.get(entry.id) ?? entry) ?? null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that order.')
      api.listEntries().then(setEntries).catch(() => {})
    }
  }

  return (
    <AppShell>
      <h1>Profile</h1>

      <section className="card profile-identity">
        <span className="profile-avatar" aria-hidden>
          {user?.username.charAt(0).toUpperCase()}
        </span>
        <div>
          <h2>{user?.username}</h2>
          <p className="muted">{user?.email}</p>
        </div>
      </section>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries === null && !error && <p className="muted">Loading your library…</p>}

      {entries !== null && (
        <>
          <ul className="profile-stats">
            <Stat label="Tracked" value={totals.tracked} />
            <Stat label="Completed" value={totals.completed} />
            <Stat
              label="Rated"
              value={totals.rated}
              hint={totals.average === null ? undefined : `${totals.average.toFixed(1)} average`}
            />
            <Stat label="Favourites" value={totals.favorites} />
          </ul>

          <section className="status-section">
            <h2>
              Favourites <span className="muted">({totals.favorites})</span>
              {favourites.length > 0 && (
                <button
                  type="button"
                  className="ghost small section-action"
                  aria-pressed={arranging}
                  onClick={() => setArranging((on) => !on)}
                >
                  {arranging ? (
                    'Done'
                  ) : (
                    <>
                      <Grip />
                      Arrange
                    </>
                  )}
                </button>
              )}
            </h2>

            {/* Said once, where it is needed: the mode is what the button already named. */}
            {arranging && (
              <p className="arrange-hint">
                Drag a cover to reorder it, or a row by its handle to move the whole row.
              </p>
            )}

            {favourites.length === 0 ? (
              <p className="muted">
                Nothing marked yet. Open an entry and use the heart in its editor to keep it
                here.
              </p>
            ) : (
              <FavouriteRows
                arranging={arranging}
                onReorder={(rows) => void reorderRows(rows.map((row) => row.key as MediaType))}
                rows={favourites.map(({ key, label, marked }) => ({
                  key,
                  label,
                  content: (
                    <FavouriteGrid
                      entries={marked}
                      arranging={arranging}
                      onReorder={(ordered) => void reorder(key, ordered)}
                    />
                  ),
                }))}
              />
            )}
          </section>

          <section className="status-section">
            <h2>Across your shelves</h2>
            <ul className="profile-shelves">
              {shelves.map(({ module, type, count }) => (
                <li key={`${module.slug}/${type.slug}`}>
                  <Link className="profile-shelf" to={`/library/${module.slug}/${type.slug}`}>
                    <span>{type.listLabel}</span>
                    <span className="muted">{count}</span>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}

    </AppShell>
  )
}
