import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  ApiError,
  api,
  type BrowseShelf,
  type FilterField,
  type FilterValues,
  type MediaType,
  type SearchResult,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { BrowseFilters } from '../components/BrowseFilters'
import { Carousel } from '../components/Carousel'
import { CatalogCard } from '../components/CatalogCard'
import { RankedRow } from '../components/RankedRow'
import { keyOf, useTrackable } from '../components/useTrackable'
import { defaultTypeOf, moduleBySlug, typeBySlug } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

/** A shelf and whatever we know about it so far. */
type ShelfState = {
  shelf: BrowseShelf
  results: SearchResult[] | null
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
            error: err instanceof ApiError ? err.message : 'Could not reach the server.',
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
      .then((found) => {
        if (cancelled) return
        setLoaded({
          mediaType,
          error: null,
          shelves: found.map((shelf) => ({ shelf, results: null, failed: false })),
        })

        // Each shelf resolves on its own so the page fills in as answers arrive, and one
        // shelf failing costs only that row rather than the whole page.
        found.forEach((shelf) =>
          api
            .browse(mediaType, shelf.id)
            .then((page) => updateShelf(shelf.id, { results: page.items }))
            .catch(() => updateShelf(shelf.id, { failed: true })),
        )
      })
      .catch((err) => {
        if (cancelled) return
        setLoaded({
          mediaType,
          shelves: [],
          error: err instanceof ApiError ? err.message : 'Could not reach the server.',
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
        <h1>Browse {active.label}</h1>

        {/*
         * Only worth showing where a module owns more than one type. Anime and manga are
         * the case it exists for; games would render a switch with one side.
         */}
        {module.types.length > 1 && (
          <div className="type-switch" role="group" aria-label={`${module.label} type`}>
            {module.types.map((type) => (
              <button
                key={type.mediaType}
                type="button"
                className={type.mediaType === active.mediaType ? 'active' : 'ghost'}
                aria-pressed={type.mediaType === active.mediaType}
                onClick={() => switchTo(type.slug)}
              >
                {type.label}
              </button>
            ))}
          </div>
        )}
      </div>

      {fields && fields.length > 0 && (
        <BrowseFilters fields={fields} values={values} onChange={narrowTo} />
      )}

      {(error || found?.error || tracking.error) && (
        <p className="alert" role="alert">
          {error ?? found?.error ?? tracking.error}
        </p>
      )}

      {/* Narrowed, the shelves give way: they answer a question nobody is asking any more. */}
      {narrowed && found === null && !grid?.error && (
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

      {narrowed && found && found.results.length > 0 && (
        <div className="cover-grid">
          {found.results.map((result) => (
            <CatalogCard
              key={keyOf(result)}
              result={result}
              state={tracking.stateOf(result)}
              onTrack={(chosen) => void tracking.track(result, chosen)}
            />
          ))}
        </div>
      )}

      {narrowed && (page > 1 || found?.hasMore) && (
        <nav className="pager" aria-label="Pages">
          <button type="button" className="ghost" disabled={page <= 1} onClick={() => turnTo(page - 1)}>
            ‹ Previous
          </button>
          <span className="muted">Page {page}</span>
          <button type="button" className="ghost" disabled={!found?.hasMore} onClick={() => turnTo(page + 1)}>
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
        shelves?.map(({ shelf, results, failed }) => (
        <section key={shelf.id} className="browse-shelf">
          <div className="browse-shelf-head">
            <h2>{shelf.label}</h2>
            <Link
              className="view-all"
              to={`/browse/${module.slug}/${active.slug}/${shelf.id}`}
            >
              View All
            </Link>
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
            <div className="ranked-list">
              {results.slice(0, 10).map((result, index) => (
                <RankedRow
                  key={keyOf(result)}
                  result={result}
                  rank={index + 1}
                  state={tracking.stateOf(result)}
                  onTrack={(chosen) => void tracking.track(result, chosen)}
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
                />
              ))}
            </Carousel>
          )}
        </section>
      ))}
    </AppShell>
  )
}
