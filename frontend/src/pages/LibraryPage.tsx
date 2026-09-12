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
      if (filters.platform && !asList(entry.metadata.platforms).includes(filters.platform)) {
        return false
      }
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

      <div className="list-layout">
        <ListSidebar type={active} entries={mine} filters={filters} onChange={setFilters} />

        <div className="list-main">
          {/*
           * The shelf holds its shape while it loads. A line of text here let the sidebar sit
           * alone against an empty column and then shoved it down when the covers arrived; the
           * placeholders are the grid the real one replaces, so nothing moves on the swap.
           */}
          {entries === null && !error && (
            <section className="status-section" aria-hidden="true">
              <h2 className="shelf-heading-pending" />
              <div className="cover-grid">
                {Array.from({ length: 12 }, (_, i) => (
                  <div key={i} className="cover-pending" />
                ))}
              </div>
            </section>
          )}

          {/*
           * Built as a shelf, not a loose paragraph. A full library opens with a shelf heading and
           * its rule on the same line as the sidebar's own; an empty one has to do the same, or the
           * sentence floats level with nothing. The message then sits where the covers would.
           */}
          {entries !== null && shown.length === 0 && (
            <section className="status-section">
              <h2>{mine.length === 0 ? 'Get started' : 'No matches'}</h2>
              <div className="shelf-empty">
                {mine.length === 0 ? (
                  <>
                    <p className="shelf-empty-line">{module.emptyHint}</p>
                    <Link
                      className="shelf-empty-action"
                      to={`/search?module=${module.slug}&type=${active.slug}`}
                    >
                      {active.searchPlaceholder}
                    </Link>
                  </>
                ) : (
                  <>
                    <p className="shelf-empty-line">Nothing matches those filters.</p>
                    <button
                      type="button"
                      className="shelf-empty-action"
                      onClick={() => setFilters(EMPTY_FILTERS)}
                    >
                      Clear filters
                    </button>
                  </>
                )}
                </div>
            </section>
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
