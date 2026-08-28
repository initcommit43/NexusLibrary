import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, api, type SearchResult } from '../api/client'
import { AppShell } from '../components/AppShell'
import { CatalogCard } from '../components/CatalogCard'
import { keyOf, useTrackable } from '../components/useTrackable'
import { defaultTypeOf, moduleBySlug, typeBySlug } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

export const SearchPage = () => {
  // Search is always inside a module: the catalogue endpoint requires a media type. The
  // query string wins when it names one, otherwise this searches wherever you already are.
  const [params, setParams] = useSearchParams()
  const module = useCurrentModule(moduleBySlug(params.get('module') ?? undefined))
  const active = typeBySlug(module, params.get('type') ?? undefined) ?? defaultTypeOf(module)

  // The term lives in the URL rather than in state, so a result set survives a reload, can
  // be linked, and can be arrived at from somewhere else — the header opens this page with
  // one already in hand. The input holds only what has been typed since.
  const query = params.get('q')?.trim() ?? ''
  const [draft, setDraft] = useState(query)
  const tracking = useTrackable()

  // What was asked, not just the term: switching shelf re-asks the same words of a
  // different catalogue, and the previous answer is not an answer to that.
  const asked = `${active.mediaType}:${query}`
  const [answer, setAnswer] = useState<{ asked: string; results: SearchResult[] } | null>(null)
  const [failure, setFailure] = useState<{ asked: string; message: string } | null>(null)

  // Held with the question they answer, so a stale or emptied term shows nothing rather
  // than the last thing that came back.
  const results = answer?.asked === asked ? answer.results : null
  const error = failure?.asked === asked ? failure.message : null

  // A question with neither an answer nor a failure against it is still in flight.
  const searching = query !== '' && results === null && error === null

  useEffect(() => {
    if (!query) return

    let current = true

    api
      .searchCatalog(active.mediaType, query)
      .then((found) => current && setAnswer({ asked, results: found }))
      .catch((err) =>
        current &&
        setFailure({
          asked,
          message: err instanceof ApiError ? err.message : 'Could not reach the server.',
        }),
      )

    // A slow answer to a term you have already moved on from must not land on the new one.
    return () => {
      current = false
    }
  }, [asked, query, active.mediaType])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const next = draft.trim()
    if (!next) return
    // Replace, so a run of refinements collapses to one step back rather than a dozen.
    setParams({ module: module.slug, type: active.slug, q: next }, { replace: true })
  }

  return (
    <AppShell module={module}>
      <h1>Search {active.label}</h1>

      <form className="search-bar" key={query} onSubmit={submit}>
        <input
          type="search"
          value={draft}
          placeholder={active.searchPlaceholder}
          aria-label={`Search ${active.label}`}
          onChange={(e) => setDraft(e.target.value)}
        />
        <button type="submit" disabled={searching || !draft.trim()}>
          {searching ? 'Searching…' : 'Search'}
        </button>
      </form>

      {(error || tracking.error) && (
        <p className="alert" role="alert">
          {error ?? tracking.error}
        </p>
      )}

      {results?.length === 0 && <p className="muted">Nothing found for “{query}”.</p>}

      <div className="cover-grid">
        {results?.map((result) => (
          <CatalogCard
            key={keyOf(result)}
            result={result}
            state={tracking.stateOf(result)}
            onTrack={(chosen) => void tracking.track(result, chosen)}
          />
        ))}
      </div>
    </AppShell>
  )
}
