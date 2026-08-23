import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useParams } from 'react-router-dom'
import { ApiError, api, type TrackedItem, type TrackingStatus } from '../api/client'
import { AppShell } from '../components/AppShell'
import { EntryCard } from '../components/EntryCard'
import { EntryEditDialog } from '../components/EntryEditDialog'
import { ListSidebar } from '../components/ListSidebar'
import { EMPTY_FILTERS, firstGenre, type ListFilters } from '../components/listFilters'
import { defaultTypeOf, moduleBySlug, typeBySlug } from '../modules/registry'

const asList = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []

export const LibraryPage = () => {
  const { module: slug, type: typeSlug } = useParams()
  const module = moduleBySlug(slug)

  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [filters, setFilters] = useState<ListFilters>(EMPTY_FILTERS)
  const [editing, setEditing] = useState<TrackedItem | null>(null)

  useEffect(() => {
    api
      .listEntries()
      .then(setEntries)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your library.'),
      )
  }, [])

  const active = module ? (typeBySlug(module, typeSlug) ?? defaultTypeOf(module)) : undefined

  // Each kind of thing has its own shelf in the header, so this page shows exactly one.
  const mine = useMemo(
    () => entries?.filter((entry) => entry.mediaType === active?.mediaType) ?? [],
    [entries, active],
  )

  const shown = useMemo(() => {
    const query = filters.query.trim().toLowerCase()
    const matching = mine.filter((entry) => {
      if (query && !entry.title.toLowerCase().includes(query)) return false
      if (filters.format && entry.metadata.format !== filters.format) return false
      if (filters.genre && !asList(entry.metadata.genres).includes(filters.genre)) return false
      return true
    })

    // Every sort falls back to title, so unscored or untouched entries keep a predictable
    // order instead of shuffling. Most of a library carries no score at all.
    const byTitle = (a: TrackedItem, b: TrackedItem) => a.title.localeCompare(b.title)

    return [...matching].sort((a, b) => {
      switch (filters.sort) {
        case 'SCORE':
          return (b.rating ?? -1) - (a.rating ?? -1) || byTitle(a, b)
        case 'PROGRESS':
          return (b.progressCurrent ?? -1) - (a.progressCurrent ?? -1) || byTitle(a, b)
        case 'GENRE': {
          const genre = firstGenre(a).localeCompare(firstGenre(b))
          // Anything with no genre sorts last rather than leading the list.
          if (!firstGenre(a)) return firstGenre(b) ? 1 : byTitle(a, b)
          if (!firstGenre(b)) return -1
          return genre || byTitle(a, b)
        }
        case 'UPDATED':
          // The list arrives most-recently-updated first, so this is the order it came in.
          return 0
        default:
          return byTitle(a, b)
      }
    })
  }, [mine, filters])

  if (!module || !active) {
    return <Navigate to="/" replace />
  }

  const sections =
    filters.status === 'ALL' ? active.statusOrder : [filters.status as TrackingStatus]

  return (
    <AppShell module={module}>
      <h1>{active.listLabel}</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries === null && !error && <p className="muted">Loading your library…</p>}

      <div className="list-layout">
        <ListSidebar type={active} entries={mine} filters={filters} onChange={setFilters} />

        <div className="list-main">
          {entries !== null && shown.length === 0 && (
            <p className="muted">
              {mine.length === 0 ? (
                <>
                  {module.emptyHint}{' '}
                  <Link to={`/search?module=${module.slug}&type=${active.slug}`}>
                    {active.searchPlaceholder}
                  </Link>
                </>
              ) : (
                'Nothing matches those filters.'
              )}
            </p>
          )}

          {sections.map((status) => {
            const group = shown.filter((entry) => entry.status === status)
            if (group.length === 0) return null

            return (
              <section key={status} className="status-section">
                <h2>
                  {active.statusLabels[status]} <span className="muted">({group.length})</span>
                </h2>

                <div className="cover-grid">
                  {group.map((entry) => (
                    <EntryCard key={entry.id} entry={entry} onEdit={() => setEditing(entry)} />
                  ))}
                </div>
              </section>
            )
          })}
        </div>
      </div>

      {editing && (
        <EntryEditDialog
          entry={editing}
          onClose={() => setEditing(null)}
          onSaved={(updated) =>
            setEntries((current) => current?.map((e) => (e.id === updated.id ? updated : e)) ?? null)
          }
          onDeleted={(id) =>
            setEntries((current) => current?.filter((e) => e.id !== id) ?? null)
          }
        />
      )}
    </AppShell>
  )
}
