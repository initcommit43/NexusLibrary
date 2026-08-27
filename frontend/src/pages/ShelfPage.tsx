import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { ApiError, api, type SearchResult, type TrackingStatus } from '../api/client'
import { AppShell } from '../components/AppShell'
import { CatalogCard } from '../components/CatalogCard'
import { RankedRow } from '../components/RankedRow'
import { STATUS_ORDER } from '../components/trackingStatus'
import { keyOf, useTrackable } from '../components/useTrackable'
import { defaultTypeOf, moduleBySlug, statusLabelsFor, typeBySlug } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

const isRanked = (shelfId: string | undefined) => shelfId === 'top'

/**
 * Matches the backend's page size. Ranks continue across pages, and deriving the offset from
 * the rows actually returned would renumber the last page, which is always short.
 */
const PAGE_SIZE = 40

/** Identifies which request an answer belongs to, so a stale one is never rendered. */
const requestKey = (mediaType: string, shelfId: string | undefined, page: number) =>
  `${mediaType}:${shelfId}:${page}`

type Loaded = {
  for: string
  results: SearchResult[]
  hasMore: boolean
  error: string | null
}

/**
 * One shelf in full — where "view all" goes.
 *
 * <p>Pages rather than scrolls on: a shelf can run to thousands of titles, and a grid that
 * only grows gives a reader no way back to where they were. The shelf's own label comes from
 * the backend rather than being spelled again here, so a renamed row renames in both places.
 */
export const ShelfPage = () => {
  const { moduleSlug, typeSlug, shelfId } = useParams()
  const module = useCurrentModule(moduleBySlug(moduleSlug))
  const active = typeBySlug(module, typeSlug) ?? defaultTypeOf(module)

  const [label, setLabel] = useState<string | null>(null)
  const [loaded, setLoaded] = useState<Loaded | null>(null)
  const [page, setPage] = useState(1)
  const [status, setStatus] = useState<TrackingStatus>('PLANNING')
  const tracking = useTrackable()

  const mediaType = active.mediaType

  // Answers carry the request they belong to, so a page turn reads as "loading" during
  // render rather than needing the previous page cleared from inside the effect.
  const fresh = loaded?.for === requestKey(mediaType, shelfId, page) ? loaded : null
  const results = fresh?.results ?? null
  const hasMore = fresh?.hasMore ?? false
  const error = fresh?.error ?? null

  useEffect(() => {
    let cancelled = false

    api
      .browseShelves(mediaType)
      .then((shelves) => {
        if (cancelled) return
        setLabel(shelves.find((shelf) => shelf.id === shelfId)?.label ?? null)
      })
      .catch(() => {
        // Only costs the heading; the grid below still loads and is the point of the page.
      })

    return () => {
      cancelled = true
    }
  }, [mediaType, shelfId])

  useEffect(() => {
    if (!shelfId) return
    let cancelled = false
    const key = requestKey(mediaType, shelfId, page)

    api
      .browse(mediaType, shelfId, page)
      .then((found) => {
        if (cancelled) return
        setLoaded({ for: key, results: found.items, hasMore: found.hasMore, error: null })
      })
      .catch((err) => {
        if (cancelled) return
        setLoaded({
          for: key,
          results: [],
          hasMore: false,
          error: err instanceof ApiError ? err.message : 'Could not reach the server.',
        })
      })

    return () => {
      cancelled = true
    }
  }, [mediaType, shelfId, page])

  const turnTo = (next: number) => {
    setPage(next)
    window.scrollTo({ top: 0 })
  }

  return (
    <AppShell module={module}>
      <div className="browse-head">
        <div>
          <p className="muted">
            <Link to={`/browse?module=${module.slug}&type=${active.slug}`}>
              ← Browse {active.label}
            </Link>
          </p>
          <h1>{label ?? active.label}</h1>
        </div>

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
      </div>

      {(error || tracking.error) && (
        <p className="alert" role="alert">
          {error ?? tracking.error}
        </p>
      )}

      {results === null ? (
        <div className="cover-grid" aria-busy="true">
          {Array.from({ length: 12 }, (_, i) => (
            <div key={i} className="card cover-card browse-skeleton" aria-hidden="true">
              <div className="cover-placeholder" />
            </div>
          ))}
        </div>
      ) : results.length === 0 && !error ? (
        <p className="muted">Nothing here right now.</p>
      ) : isRanked(shelfId) ? (
        <div className="ranked-list">
          {results.map((result, index) => (
            <RankedRow
              key={keyOf(result)}
              result={result}
              // Continues across pages, so #41 is the first row of page two.
              rank={(page - 1) * PAGE_SIZE + index + 1}
              state={tracking.stateOf(result)}
              onTrack={(chosen) => void tracking.track(result, chosen)}
            />
          ))}
        </div>
      ) : (
        <div className="cover-grid">
          {results.map((result) => (
            <CatalogCard
              key={keyOf(result)}
              result={result}
              state={tracking.stateOf(result)}
              onTrack={(chosen) => void tracking.track(result, chosen)}
            />
          ))}
        </div>
      )}

      {(page > 1 || hasMore) && (
        <nav className="pager" aria-label="Pages">
          <button type="button" className="ghost" disabled={page <= 1} onClick={() => turnTo(page - 1)}>
            ‹ Previous
          </button>
          <span className="muted">Page {page}</span>
          <button type="button" className="ghost" disabled={!hasMore} onClick={() => turnTo(page + 1)}>
            Next ›
          </button>
        </nav>
      )}
    </AppShell>
  )
}
