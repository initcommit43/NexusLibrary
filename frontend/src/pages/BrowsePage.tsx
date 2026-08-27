import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  ApiError,
  api,
  type BrowseShelf,
  type MediaType,
  type SearchResult,
  type TrackingStatus,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { Carousel } from '../components/Carousel'
import { CatalogCard } from '../components/CatalogCard'
import { RankedRow } from '../components/RankedRow'
import { STATUS_ORDER } from '../components/trackingStatus'
import { keyOf, useTrackable } from '../components/useTrackable'
import { defaultTypeOf, moduleBySlug, statusLabelsFor, typeBySlug } from '../modules/registry'
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
  const [status, setStatus] = useState<TrackingStatus>('PLANNING')
  const tracking = useTrackable()

  const mediaType = active.mediaType
  const current = loaded?.mediaType === mediaType ? loaded : null
  const shelves = current?.shelves ?? null
  const error = current?.error ?? null

  useEffect(() => {
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
  }, [mediaType])

  const switchTo = (slug: string) => {
    const next = new URLSearchParams(params)
    next.set('module', module.slug)
    next.set('type', slug)
    setParams(next, { replace: true })
  }

  return (
    <AppShell module={module}>
      <div className="browse-head">
        <h1>Browse {active.label}</h1>

        <div className="browse-controls">
          <select
            value={status}
            aria-label="Status to track as"
            onChange={(e) => setStatus(e.target.value as TrackingStatus)}
          >
            {STATUS_ORDER.map((option) => (
              <option key={option} value={option}>
                Add as {statusLabelsFor(active.mediaType)[option]}
              </option>
            ))}
          </select>

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
      </div>

      {(error || tracking.error) && (
        <p className="alert" role="alert">
          {error ?? tracking.error}
        </p>
      )}

      {shelves !== null && shelves.length === 0 && !error && (
        <p className="muted">
          Nothing to browse here yet. <Link to="/search">Search</Link> to find something by
          name.
        </p>
      )}

      {shelves?.map(({ shelf, results, failed }) => (
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
                  onTrack={() => void tracking.track(result, status)}
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
