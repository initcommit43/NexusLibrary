import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api, type TrackedItem } from '../api/client'
import { AppShell } from '../components/AppShell'
import { EntryCard } from '../components/EntryCard'
import { EntryEditDialog } from '../components/EntryEditDialog'
import { useAuth } from '../auth/useAuth'
import { MODULES, moduleForMediaType } from '../modules/registry'

/** Ratings are stored 0–100 and shown out of ten, the way every entry shows its own. */
const RATING_SCALE = 10

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
  const [editing, setEditing] = useState<TrackedItem | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .listEntries()
      .then(setEntries)
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
   * Grouped by module, in the order the app lists them, and only the modules that have any.
   * A heading over an empty grid says the module exists; here it would only say you have
   * nothing in it, which the shelf counts below already say better.
   */
  const favourites = useMemo(() => {
    const marked = (entries ?? []).filter((entry) => entry.favorite)

    return MODULES.map((module) => ({
      module,
      marked: marked.filter((entry) => moduleForMediaType(entry.mediaType)?.slug === module.slug),
    })).filter((group) => group.marked.length > 0)
  }, [entries])

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

  const replace = (updated: TrackedItem) =>
    setEntries((held) => held?.map((entry) => (entry.id === updated.id ? updated : entry)) ?? null)

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
            </h2>

            {favourites.length === 0 ? (
              <p className="muted">
                Nothing marked yet. Open an entry and use the heart in its editor to keep it
                here.
              </p>
            ) : (
              favourites.map(({ module, marked }) => (
                <section key={module.slug} className="profile-favourites">
                  <h3>{module.label}</h3>
                  <div className="cover-grid">
                    {marked.map((entry) => (
                      <EntryCard key={entry.id} entry={entry} onEdit={() => setEditing(entry)} />
                    ))}
                  </div>
                </section>
              ))
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

      {editing && (
        <EntryEditDialog
          entry={editing}
          onClose={() => setEditing(null)}
          onSaved={(updated) => {
            replace(updated)
            setEditing(null)
          }}
          onDeleted={(id) => {
            setEntries((held) => held?.filter((entry) => entry.id !== id) ?? null)
            setEditing(null)
          }}
        />
      )}
    </AppShell>
  )
}
