import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  api,
  errorMessage,
  type BrowseShelf,
  type FilterField,
  type FilterValues,
  type MediaType,
  type SearchResult,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { EntryEditDialog } from '../components/EntryEditDialog'
import { BrowseFilters } from '../components/BrowseFilters'
import { BrowseListRow } from '../components/BrowseListRow'
import { BrowseToolbar, type BrowseView } from '../components/BrowseToolbar'
import { Carousel } from '../components/Carousel'
import { CatalogCard } from '../components/CatalogCard'
import { TypeSwitch } from '../components/TypeSwitch'
import { useNarrowScreen } from '../components/useNarrowScreen'
import { keyOf, useTrackable } from '../components/useTrackable'
import { defaultTypeOf, moduleBySlug, typeBySlug } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

/** A shelf and whatever we know about it so far. */
type ShelfState = {
  shelf: BrowseShelf
  results: SearchResult[] | null
  hasMore: boolean
  failed: boolean
}

/**
 * Shelves carry the media type they were loaded for, so switching type reads as "not loaded
 * yet" during render instead of needing a reset written into the effect — which would show
 * the previous type's rows for a frame on the way past.
 */
type Loaded = {
  mediaType: MediaType
  shelves: ShelfState[]
  error: string | null
}

/** A shelf whose id says it ranks its rows is read down, not across. */
const isRanked = (shelfId: string) => shelfId === 'top'

/** Search params the page owns itself; everything else in the URL is a filter value. */
const RESERVED = new Set(['module', 'type', 'page'])

const valuesFrom = (params: URLSearchParams): FilterValues => {
  const values: FilterValues = {}
  for (const [field, value] of params) {
    if (RESERVED.has(field) || !value) continue
    values[field] = [...(values[field] ?? []), value]
  }
  return values
}

const isNarrowed = (values: FilterValues) =>
  Object.values(values).some((chosen) => chosen.some(Boolean))

const pageIn = (params: URLSearchParams) => Math.max(1, Number(params.get('page') ?? 1) || 1)

/** Where the chosen layout is remembered: a way of looking, kept per browser, not per account. */
const VIEW_KEY = 'nexus.browse-view'

const storedView = (): BrowseView => {
  try {
    return localStorage.getItem(VIEW_KEY) === 'list' ? 'list' : 'grid'
  } catch {
    return 'grid'
  }
}

/** Long enough that a typed word is one request rather than one per letter. */
const SETTLE_MS = 300

/**
 * Discovery — what is trending, what is coming — as opposed to the shelves, which are yours.
 *
 * <p>Which rows appear is the backend's answer, not this page's: each module's adapter names
 * its own, so a module gains a browse page by implementing two methods and this file does not
 * change. That is also what makes the anime/manga switch cheap — the two are different media
 * types with different shelves, and switching simply asks for the other one's.
 */
export const BrowsePage = () => {
  const [params, setParams] = useSearchParams()
  const module = useCurrentModule(moduleBySlug(params.get('module') ?? undefined))
  const active = typeBySlug(module, params.get('type') ?? undefined) ?? defaultTypeOf(module)

  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const tracking = useTrackable()
  const narrow = useNarrowScreen()

  const mediaType = active.mediaType
  const current = loaded?.mediaType === mediaType ? loaded : null
  const shelves = current?.shelves ?? null
  const error = current?.error ?? null

  // The values live in the URL, so a narrowed page can be linked, reloaded, and stepped back
  // out of one control at a time.
  const values = valuesFrom(params)
  const narrowed = isNarrowed(values)
  const page = pageIn(params)

  const [bar, setBar] = useState<{ mediaType: MediaType; fields: FilterField[] } | null>(null)
  const [view, setView] = useState<BrowseView>(storedView)
  const chooseView = (next: BrowseView) => {
    setView(next)
    try {
      localStorage.setItem(VIEW_KEY, next)
    } catch {
      // Private windows refuse storage; the layout simply is not remembered there.
    }
  }
  const fields = bar?.mediaType === mediaType ? bar.fields : null

  // Typing narrows on every keystroke; asking the source on every keystroke would spend the
  // rate limit on answers nobody waited to read.
  const asked = params.toString()
  const [settled, setSettled] = useState(asked)
  const [grid, setGrid] = useState<{
    asked: string
    results: SearchResult[]
    hasMore: boolean
    error: string | null
  } | null>(null)

  const found = grid?.asked === settled ? grid : null

  // While the next answer is in flight the previous one stays where it is, dimmed. Swapping a
  // full grid for a short row of skeletons and back reflows the page twice and moves every
  // card under the pointer, to say nothing the reader did not already know.
  const showing = found ?? (grid && grid.results.length > 0 ? grid : null)
  const awaiting = narrowed && found === null

  useEffect(() => {
    const settling = setTimeout(() => setSettled(asked), SETTLE_MS)
    return () => clearTimeout(settling)
  }, [asked])

  useEffect(() => {
    let cancelled = false
    api
      .browseFilters(mediaType)
      .then((declared) => !cancelled && setBar({ mediaType, fields: declared }))
      // A bar that cannot be described is a bar the page does without, not a broken page.
      .catch(() => !cancelled && setBar({ mediaType, fields: [] }))

    return () => {
      cancelled = true
    }
  }, [mediaType])

  useEffect(() => {
    const asking = new URLSearchParams(settled)
    if (!isNarrowed(valuesFrom(asking))) return

    let cancelled = false
    api
      .discover(mediaType, valuesFrom(asking), pageIn(asking))
      .then(
        (results) =>
          !cancelled &&
          setGrid({
            asked: settled,
            results: results.items,
            hasMore: results.hasMore,
            error: null,
          }),
      )
      .catch(
        (err) =>
          !cancelled &&
          setGrid({
            asked: settled,
            results: [],
            hasMore: false,
            error: errorMessage(err, 'Could not reach the server.'),
          }),
      )

    return () => {
      cancelled = true
    }
  }, [mediaType, settled])

  useEffect(() => {
    if (narrowed) return

    let cancelled = false

    /** Rewrites one shelf, and only while that shelf still belongs on screen. */
    const updateShelf = (shelfId: string, change: Partial<ShelfState>) =>
      setLoaded((previous) =>
        !previous || previous.mediaType !== mediaType || cancelled
          ? previous
          : {
              ...previous,
              shelves: previous.shelves.map((entry) =>
                entry.shelf.id === shelfId ? { ...entry, ...change } : entry,
              ),
            },
      )

    api
      .browseShelves(mediaType)
      .then((all) => {
        if (cancelled) return

        // A module may keep a row for its home page alone; browse shows what is browsable.
        const found = all.filter((shelf) => shelf.onBrowse)

        setLoaded({
          mediaType,
          error: null,
          shelves: found.map((shelf) => ({ shelf, results: null, hasMore: false, failed: false })),
        })

        // Each shelf resolves on its own so the page fills in as answers arrive, and one
        // shelf failing costs only that row rather than the whole page.
        found.forEach((shelf) =>
          api
            .browse(mediaType, shelf.id)
            .then((page) => updateShelf(shelf.id, { results: page.items, hasMore: page.hasMore }))
            .catch(() => updateShelf(shelf.id, { failed: true })),
        )
      })
      .catch((err) => {
        if (cancelled) return
        setLoaded({
          mediaType,
          shelves: [],
          error: errorMessage(err, 'Could not reach the server.'),
        })
      })

    return () => {
      cancelled = true
    }
  }, [mediaType, narrowed])

  const switchTo = (slug: string) => {
    const next = new URLSearchParams(params)
    next.set('module', module.slug)
    next.set('type', slug)
    setParams(next, { replace: true })
  }

  /** Writes the bar back into the URL. Page is dropped: a new question starts at its first. */
  const narrowTo = (next: FilterValues) => {
    const search = new URLSearchParams()
    search.set('module', module.slug)
    search.set('type', active.slug)
    for (const [field, chosen] of Object.entries(next)) {
      for (const value of chosen) {
        if (value) search.append(field, value)
      }
    }
    setParams(search, { replace: true })
  }

  const turnTo = (next: number) => {
    const search = new URLSearchParams(params)
    search.set('page', String(next))
    setParams(search)
  }

  return (
    <AppShell module={module}>
      <div className="browse-head">
        {/* A phone names the page alone: the switch beside it already says which type. */}
        <h1 className="page-title">{narrow ? 'Browse' : `Browse ${active.label}`}</h1>

        <TypeSwitch module={module} active={active} onSwitch={(type) => switchTo(type.slug)} />
      </div>

      {fields && fields.length > 0 && (
        <BrowseFilters fields={fields} values={values} onChange={narrowTo} />
      )}

      {narrowed && fields && fields.length > 0 && (
        <BrowseToolbar fields={fields} values={values} onChange={narrowTo} view={view} onView={chooseView} />
      )}

      {(error || found?.error || tracking.error) && (
        <p className="alert" role="alert">
          {error ?? found?.error ?? tracking.error}
        </p>
      )}

      {/* Narrowed, the shelves give way: they answer a question nobody is asking any more. */}
      {narrowed && awaiting && showing === null && !grid?.error && (
        <div className="cover-grid" aria-busy="true">
          {Array.from({ length: 12 }, (_, i) => (
            <div key={i} className="card cover-card browse-skeleton" aria-hidden="true">
              <div className="cover-placeholder" />
            </div>
          ))}
        </div>
      )}

      {narrowed && found && found.results.length === 0 && !found.error && (
        <p className="muted">Nothing matches those filters.</p>
      )}

      {narrowed && showing && showing.results.length > 0 && view === 'list' && (
        <div className={awaiting ? 'browse-list is-stale' : 'browse-list'} aria-busy={awaiting}>
          {showing.results.map((result) => (
            <BrowseListRow key={keyOf(result)} result={result} fields={fields ?? []} />
          ))}
        </div>
      )}

      {narrowed && showing && showing.results.length > 0 && view === 'grid' && (
        <div className={awaiting ? 'cover-grid is-stale' : 'cover-grid'} aria-busy={awaiting}>
          {showing.results.map((result) => (
            <CatalogCard
              key={keyOf(result)}
              result={result}
              state={tracking.stateOf(result)}
              onTrack={(chosen) => void tracking.track(result, chosen)}
              onEdit={() => void tracking.edit(result)}
            />
          ))}
        </div>
      )}

      {narrowed && (page > 1 || showing?.hasMore) && (
        <nav className="pager" aria-label="Pages">
          <button type="button" className="ghost" disabled={page <= 1} onClick={() => turnTo(page - 1)}>
            ‹ Previous
          </button>
          <span className="muted">Page {page}</span>
          <button type="button" className="ghost" disabled={!showing?.hasMore} onClick={() => turnTo(page + 1)}>
            Next ›
          </button>
        </nav>
      )}

      {!narrowed && shelves !== null && shelves.length === 0 && !error && (
        <p className="muted">
          Nothing to browse here yet. <Link to="/search">Search</Link> to find something by
          name.
        </p>
      )}

      {!narrowed &&
        shelves?.map(({ shelf, results, hasMore, failed }) => (
        <section key={shelf.id} className="browse-shelf">
          <div className="browse-shelf-head">
            <h2>{shelf.label}</h2>
            {/* Only where the row is a window onto more; otherwise it is the whole shelf. */}
            {hasMore && (
              <Link
                className="view-all"
                to={`/browse/${module.slug}/${active.slug}/${shelf.id}`}
              >
                View All
              </Link>
            )}
          </div>

          {failed ? (
            <p className="muted">This row could not be loaded.</p>
          ) : results === null ? (
            <div className="browse-row" aria-busy="true">
              {Array.from({ length: 6 }, (_, i) => (
                <div key={i} className="card cover-card browse-skeleton" aria-hidden="true">
                  <div className="cover-placeholder" />
                </div>
              ))}
            </div>
          ) : results.length === 0 ? (
            <p className="muted">Nothing here right now.</p>
          ) : isRanked(shelf.id) ? (
            <div className="browse-list">
              {results.slice(0, 10).map((result, index) => (
                <BrowseListRow
                  key={keyOf(result)}
                  result={result}
                  fields={fields ?? []}
                  rank={index + 1}
                />
              ))}
            </div>
          ) : (
            <Carousel label={shelf.label}>
              {results.map((result) => (
                <CatalogCard
                  key={keyOf(result)}
                  result={result}
                  state={tracking.stateOf(result)}
                  onTrack={(chosen) => void tracking.track(result, chosen)}
                  onEdit={() => void tracking.edit(result)}
                />
              ))}
            </Carousel>
          )}
        </section>
      ))}
      {tracking.editing && (
        <EntryEditDialog
          entry={tracking.editing}
          onClose={tracking.closeEditor}
          onSaved={tracking.saved}
          onDeleted={tracking.deleted}
        />
      )}
    </AppShell>
  )
}
