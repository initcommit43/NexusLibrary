import { useEffect, useMemo, useState } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom'
import { api, errorMessage, type TrackedItem, type TrackingStatus } from '../api/client'
import { AppShell } from '../components/AppShell'
import { EntryCard } from '../components/EntryCard'
import { EntryEditDialog } from '../components/EntryEditDialog'
import { EntryRow } from '../components/EntryRow'
import { EntryTable } from '../components/EntryTable'
import { LibraryPhoneFilters } from '../components/LibraryPhoneFilters'
import { ListSidebar } from '../components/ListSidebar'
import { EMPTY_FILTERS, firstGenre, type ListFilters } from '../components/listFilters'
import { TypeSwitch } from '../components/TypeSwitch'
import { useLibraryView } from '../components/useLibraryView'
import { useNarrowScreen } from '../components/useNarrowScreen'
import { defaultTypeOf, moduleBySlug, typeBySlug } from '../modules/registry'

const ListIcon = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" aria-hidden>
    <path d="M5 7h14M5 12h14M5 17h14" strokeWidth="2" strokeLinecap="round" />
  </svg>
)

const GridIcon = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden>
    <rect x="4.5" y="4.5" width="6" height="6" rx="1.5" />
    <rect x="13.5" y="4.5" width="6" height="6" rx="1.5" />
    <rect x="4.5" y="13.5" width="6" height="6" rx="1.5" />
    <rect x="13.5" y="13.5" width="6" height="6" rx="1.5" />
  </svg>
)

const asList = (value: unknown): string[] =>
  Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []

export const LibraryPage = () => {
  const { module: slug, type: typeSlug } = useParams()
  const module = moduleBySlug(slug)
  const navigate = useNavigate()
  const narrow = useNarrowScreen()

  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  // A phone opens on the in-progress list, as the native app does; wide shows every section.
  const [filters, setFilters] = useState<ListFilters>(() =>
    narrow ? { ...EMPTY_FILTERS, status: 'IN_PROGRESS' } : EMPTY_FILTERS,
  )
  const [editing, setEditing] = useState<TrackedItem | null>(null)
  const [view, setView] = useLibraryView()

  useEffect(() => {
    api
      .listEntries()
      .then(setEntries)
      .catch((err) =>
        setError(errorMessage(err, 'Could not load your library.')),
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

  /*
   * A phone follows the native app: one flat shelf under All, in the chosen sort, with the chips
   * naming the list in place of section headings. Wide keeps a section per status.
   */
  const statuses =
    filters.status === 'ALL' ? active.statusOrder : [filters.status as TrackingStatus]
  const sections: { status: TrackingStatus | null; group: TrackedItem[] }[] = narrow
    ? [{ status: null, group: shown.filter((entry) => statuses.includes(entry.status)) }]
    : statuses.map((status) => ({
        status,
        group: shown.filter((entry) => entry.status === status),
      }))

  /*
   * It shows the view it switches to, as the native app's toggle does, and is named so. A phone
   * keeps it beside the page's title; wide, it rides the first shelf's heading, so it lines up
   * with the covers it rearranges rather than with a title a sidebar's width away from them.
   */
  const viewToggle = (
    <button
      type="button"
      className={narrow ? 'ghost icon-button' : 'ghost icon-button library-view-toggle'}
      aria-label={view === 'grid' ? 'Show as list' : 'Show as grid'}
      title={view === 'grid' ? 'Show as list' : 'Show as grid'}
      onClick={() => setView(view === 'grid' ? 'list' : 'grid')}
    >
      {view === 'grid' ? <ListIcon /> : <GridIcon />}
    </button>
  )
  const firstShelf = sections.findIndex(({ group }) => group.length > 0)

  const replace = (updated: TrackedItem) =>
    setEntries((current) => current?.map((e) => (e.id === updated.id ? updated : e)) ?? null)

  return (
    <AppShell module={module}>
      {/*
       * A phone has no header shelves, and its tab bar opens one library whatever the type, so
       * the page is named for that and the switch under it does the shelves' job.
       */}
      <div className="library-head">
        <h1 className="page-title">{narrow ? 'Library' : active.label}</h1>
        {narrow && viewToggle}
      </div>
      <TypeSwitch
        className="library-type-switch"
        module={module}
        active={active}
        onSwitch={(type) => navigate(`/library/${module.slug}/${type.slug}`)}
      />

      {narrow && (
        <LibraryPhoneFilters type={active} entries={mine} filters={filters} onChange={setFilters} />
      )}

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      <div className="list-layout">
        {!narrow && (
          <ListSidebar type={active} entries={mine} filters={filters} onChange={setFilters} />
        )}

        <div className="list-main">
          {/*
           * The shelf holds its shape while it loads. A line of text here let the sidebar sit
           * alone against an empty column and then shoved it down when the covers arrived; the
           * placeholders are the grid the real one replaces, so nothing moves on the swap.
           */}
          {entries === null && !error && (
            <section className="status-section" aria-hidden="true">
              {!narrow && <h2 className="shelf-heading-pending" />}
              {view === 'grid' ? (
                <div className="cover-grid library-grid">
                  {Array.from({ length: 12 }, (_, i) => (
                    <div key={i} className="cover-pending" />
                  ))}
                </div>
              ) : (
                <ul className="entry-list">
                  {Array.from({ length: 6 }, (_, i) => (
                    <li key={i} className="entry-row is-pending">
                      <div className="entry-row-cover" />
                    </li>
                  ))}
                </ul>
              )}
            </section>
          )}

          {/*
           * Built as a shelf, not a loose paragraph. A full library opens with a shelf heading and
           * its rule on the same line as the sidebar's own; an empty one has to do the same, or the
           * sentence floats level with nothing. The message then sits where the covers would.
           */}
          {entries !== null && shown.length === 0 && (
            <section className="status-section">
              <h2>
                {mine.length === 0 ? 'Get started' : 'No matches'}
                {!narrow && viewToggle}
              </h2>
              <div className="shelf-empty">
                {mine.length === 0 ? (
                  <>
                    <p className="shelf-empty-line">
                      {module.emptyHint.lead} <Link to="/settings">settings</Link>
                      {module.emptyHint.tail}
                    </p>
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

          {sections.map(({ status, group }, index) => {
            if (group.length === 0) return null

            return (
              <section key={status ?? 'ALL'} className="status-section">
                {status && (
                  <h2>
                    {active.statusLabels[status]} <span className="muted">({group.length})</span>
                    {!narrow && index === firstShelf && viewToggle}
                  </h2>
                )}

                {view === 'grid' ? (
                  <div className="cover-grid library-grid">
                    {group.map((entry) => (
                      <EntryCard
                        key={entry.id}
                        entry={entry}
                        onEdit={() => setEditing(entry)}
                        onChanged={narrow ? replace : undefined}
                      />
                    ))}
                  </div>
                ) : narrow ? (
                  <ul className="entry-list">
                    {group.map((entry) => (
                      <EntryRow
                        key={entry.id}
                        entry={entry}
                        onChanged={replace}
                        onEdit={() => setEditing(entry)}
                      />
                    ))}
                  </ul>
                ) : (
                  <EntryTable
                    entries={group}
                    columns={active.listColumns}
                    onChanged={replace}
                    onEdit={setEditing}
                  />
                )}
              </section>
            )
          })}
        </div>
      </div>

      {editing && (
        <EntryEditDialog
          entry={editing}
          onClose={() => setEditing(null)}
          onSaved={replace}
          onDeleted={(id) =>
            setEntries((current) => current?.filter((e) => e.id !== id) ?? null)
          }
        />
      )}
    </AppShell>
  )
}
