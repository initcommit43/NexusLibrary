import { useState, type FormEvent } from 'react'
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
  const [params] = useSearchParams()
  const module = useCurrentModule(moduleBySlug(params.get('module') ?? undefined))
  const active = typeBySlug(module, params.get('type') ?? undefined) ?? defaultTypeOf(module)

  const [query, setQuery] = useState('')
  const [results, setResults] = useState<SearchResult[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [searching, setSearching] = useState(false)
  const tracking = useTrackable()

  const runSearch = async (event: FormEvent) => {
    event.preventDefault()
    if (!query.trim()) return

    setSearching(true)
    setError(null)
    try {
      setResults(await api.searchCatalog(active.mediaType, query.trim()))
    } catch (err) {
      setResults(null)
      setError(err instanceof ApiError ? err.message : 'Could not reach the server.')
    } finally {
      setSearching(false)
    }
  }

  return (
    <AppShell module={module}>
      <h1>Search {active.label}</h1>

      <form className="search-bar" onSubmit={runSearch}>
        <input
          type="search"
          value={query}
          placeholder={active.searchPlaceholder}
          aria-label={`Search ${active.label}`}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button type="submit" disabled={searching || !query.trim()}>
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
