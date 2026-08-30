import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { ApiError, api, type SearchResult } from '../api/client'
import { AppShell } from '../components/AppShell'
import { CatalogCard } from '../components/CatalogCard'
import { EntryEditDialog } from '../components/EntryEditDialog'
import { keyOf, useTrackable } from '../components/useTrackable'

/** What a work with no date yet is filed under, as the source itself files it. */
const UNDATED = 'TBA'

/**
 * A studio's work, grouped by the year it came out.
 *
 * <p>Newest first, and undated work above all of it: what a studio has coming is the part
 * anyone following them is looking for, and a release with no date is a release announced.
 */
const byYear = (works: SearchResult[], newestFirst: boolean) => {
  const years = new Map<string, SearchResult[]>()

  for (const work of works) {
    const year = work.releaseDate ? work.releaseDate.slice(0, 4) : UNDATED
    years.set(year, [...(years.get(year) ?? []), work])
  }

  return [...years.entries()].sort(([one], [other]) => {
    if (one === UNDATED) return -1
    if (other === UNDATED) return 1
    return newestFirst ? other.localeCompare(one) : one.localeCompare(other)
  })
}

/**
 * Everything one studio made.
 *
 * <p>Reached from the studios and producers on a title's page, which is where the question is
 * asked: having watched something, what else are these people behind. Grouped by year rather
 * than listed flat, because a studio's catalogue is read as a history.
 */
export const StudioPage = () => {
  const { source, studioId } = useParams()
  const tracking = useTrackable()

  const [name, setName] = useState<string | null>(null)
  const [works, setWorks] = useState<SearchResult[] | null>(null)
  const [page, setPage] = useState(1)
  const [hasMore, setHasMore] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [mineOnly, setMineOnly] = useState(false)
  const [newestFirst, setNewestFirst] = useState(true)

  useEffect(() => {
    let cancelled = false
    if (!source || !studioId) return

    api
      .studioWorks(source, studioId, page)
      .then((answer) => {
        if (cancelled) return
        setName(answer.name)
        // Pages add to what is shown rather than replacing it: a catalogue is read down.
        setWorks((held) => (page === 1 ? answer.items : [...(held ?? []), ...answer.items]))
        setHasMore(answer.hasMore)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err instanceof ApiError ? err.message : 'Could not reach the server.')
      })

    return () => {
      cancelled = true
    }
  }, [source, studioId, page])

  const shown = (works ?? []).filter(
    (work) => !mineOnly || tracking.stateOf(work) === 'tracked',
  )
  const grouped = byYear(shown, newestFirst)

  return (
    <AppShell>
      <div className="studio-head">
        {/* Named once the source says what it is called. Calling it a studio in the meantime
            is a guess that is wrong half the time — the same page holds producers. */}
        <h1>{name ?? ' '}</h1>

        <div className="studio-controls">
          <label className="check">
            <input
              type="checkbox"
              checked={mineOnly}
              onChange={(event) => setMineOnly(event.target.checked)}
            />
            <span>On my list</span>
          </label>

          <button type="button" className="ghost small" onClick={() => setNewestFirst((on) => !on)}>
            {newestFirst ? 'Newest first' : 'Oldest first'}
          </button>
        </div>
      </div>

      {(error || tracking.error) && (
        <p className="alert" role="alert">
          {error ?? tracking.error}
        </p>
      )}

      {works === null && !error && <p className="muted">Loading…</p>}

      {works !== null && shown.length === 0 && !error && (
        <p className="muted">
          {mineOnly ? 'Nothing of theirs is on your list yet.' : 'Nothing listed for them.'}
        </p>
      )}

      {grouped.map(([year, titles]) => (
        <section key={year} className="status-section">
          <h2>{year}</h2>
          <div className="cover-grid">
            {titles.map((work) => (
              <CatalogCard
                key={keyOf(work)}
                result={work}
                state={tracking.stateOf(work)}
                onTrack={(chosen) => void tracking.track(work, chosen)}
                onEdit={() => void tracking.edit(work)}
              />
            ))}
          </div>
        </section>
      ))}

      {hasMore && (
        <button type="button" className="ghost feed-more" onClick={() => setPage((held) => held + 1)}>
          Load more
        </button>
      )}

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
