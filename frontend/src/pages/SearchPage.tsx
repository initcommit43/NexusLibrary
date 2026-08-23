import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { ApiError, api, type SearchResult, type TrackingStatus } from '../api/client'
import { AppShell } from '../components/AppShell'
import { STATUS_ORDER } from '../components/trackingStatus'
import { moduleBySlug } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

type TrackState = Record<string, 'idle' | 'saving' | 'tracked'>

export const SearchPage = () => {
  // Search is always inside a module: the catalogue endpoint requires a media type. The
  // query string wins when it names one, otherwise this searches wherever you already are.
  const [params] = useSearchParams()
  const module = useCurrentModule(moduleBySlug(params.get('module') ?? undefined))

  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResult[] | null>(null)
  const [status, setStatus] = useState<TrackingStatus>('PLANNING')
  const [trackState, setTrackState] = useState<TrackState>({})
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)
  const [alreadyTracked, setAlreadyTracked] = useState<Set<string>>(new Set())

  useEffect(() => {
    api
      .listEntries()
      .then((entries) => setAlreadyTracked(new Set(entries.map((e) => `${e.source}:${e.externalId}`))))
      .catch(() => {
        // A failure here only costs the "already tracked" hint, so the page still works.
      })
  }, [])

  const runSearch = async (event: FormEvent) => {
    event.preventDefault()
    if (!query.trim()) return

    setSearching(true)
    setError(null)
    try {
      setResults(await api.searchCatalog(module.defaultMediaType, query.trim()))
    } catch (err) {
      setResults(null)
      setError(err instanceof ApiError ? err.message : 'Could not reach the server.')
    } finally {
      setSearching(false)
    }
  }

  const track = async (result: SearchResult) => {
    const key = `${result.source}:${result.externalId}`
    setTrackState((s) => ({ ...s, [key]: 'saving' }))
    setError(null)

    try {
      await api.createEntry({ source: result.source, externalId: result.externalId, status })
      setTrackState((s) => ({ ...s, [key]: 'tracked' }))
      setAlreadyTracked((tracked) => new Set(tracked).add(key))
    } catch (err) {
      setTrackState((s) => ({ ...s, [key]: 'idle' }))
      setError(err instanceof ApiError ? err.message : 'Could not save that. Please try again.')
    }
  }

  return (
    <AppShell module={module}>
      <h1>Search {module.label}</h1>

      <form className="search-bar" onSubmit={runSearch}>
        <input
          type="search"
          value={query}
          placeholder={module.searchPlaceholder}
          aria-label={`Search ${module.label}`}
          onChange={(e) => setQuery(e.target.value)}
        />
        <select
          value={status}
          aria-label="Status to track as"
          onChange={(e) => setStatus(e.target.value as TrackingStatus)}
        >
          {STATUS_ORDER.map((option) => (
            <option key={option} value={option}>
              Add as {module.statusLabels[option]}
            </option>
          ))}
        </select>
        <button type="submit" disabled={searching || !query.trim()}>
          {searching ? 'Searching…' : 'Search'}
        </button>
      </form>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {results?.length === 0 && <p className="muted">Nothing found for “{query}”.</p>}

      <div className="cover-grid">
        {results?.map((result) => {
          const key = `${result.source}:${result.externalId}`
          const state = trackState[key] ?? (alreadyTracked.has(key) ? 'tracked' : 'idle')

          return (
            <article key={key} className="card cover-card">
              {result.coverUrl ? (
                <img src={result.coverUrl} alt="" loading="lazy" />
              ) : (
                <div className="cover-placeholder" aria-hidden="true" />
              )}
              <div className="cover-body">
                <h2>{result.title}</h2>
                <p className="muted">{result.releaseDate?.slice(0, 4) ?? 'Unreleased'}</p>
                <button
                  type="button"
                  className={state === 'tracked' ? 'ghost' : ''}
                  disabled={state !== 'idle'}
                  onClick={() => void track(result)}
                >
                  {state === 'saving' ? 'Saving…' : state === 'tracked' ? 'Tracked' : 'Track'}
                </button>
              </div>
            </article>
          )
        })}
      </div>
    </AppShell>
  )
}
