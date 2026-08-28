import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, type SearchResult } from '../api/client'
import { keyOf } from './useTrackable'
import type { MediaTypeDefinition, ModuleDefinition } from '../modules/registry'

/** Below this a term matches most of the catalogue and answers nothing useful. */
const MIN_TERM = 2

/** Long enough that a typed word is one request rather than one per letter. */
const SETTLE_MS = 250

/** The rest are a click away on the results page; this is a glance, not a list. */
const SHOWN = 8

const SearchIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <circle cx="11" cy="11" r="7" strokeWidth="1.8" />
    <path d="m20 20-3.5-3.5" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
)

const ClearIcon = () => (
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 6 12 12M18 6 6 18" strokeWidth="2" strokeLinecap="round" />
  </svg>
)

const yearOf = (result: SearchResult) => result.releaseDate?.slice(0, 4) ?? null

const formatOf = (result: SearchResult) =>
  typeof result.facets?.format === 'string' && result.facets.format.trim() !== ''
    ? result.facets.format
    : null

/**
 * Catalogue search for the shelf you are standing on.
 *
 * <p>An overlay rather than a field in the header: it matters only for the few seconds you
 * are typing into it, and the header already spends its width on the shelves of whichever
 * module you are in. Scoping it to the current media type is the point — the app knows you
 * are looking at games, so it does not ask.
 *
 * <p>Answers arrive under the field as you type, because going to a page to read them costs
 * a navigation for something you are usually about to leave again immediately.
 */
export const HeaderSearch = ({
  module,
  type,
}: {
  module: ModuleDefinition
  type: MediaTypeDefinition
}) => {
  const [open, setOpen] = useState(false)
  const [draft, setDraft] = useState('')
  const [term, setTerm] = useState('')
  const input = useRef<HTMLInputElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const navigate = useNavigate()

  // Held with the question they answer, so a stale or emptied term shows nothing rather
  // than the last thing that came back.
  const asked = `${type.mediaType}:${term}`
  const [answer, setAnswer] = useState<{ asked: string; results: SearchResult[] } | null>(null)
  const [failure, setFailure] = useState<{ asked: string; message: string } | null>(null)

  const results = answer?.asked === asked ? answer.results : null
  const error = failure?.asked === asked ? failure.message : null
  const ready = term.length >= MIN_TERM
  const searching = ready && results === null && error === null

  useEffect(() => {
    if (!open) return

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [open])

  useEffect(() => {
    // Opening it is the whole gesture; nobody wants a second click to reach the field.
    if (open) input.current?.focus()
    else trigger.current?.focus({ preventScroll: true })
  }, [open])

  // A pause in the typing is the question; every keystroke before it was a guess at one.
  useEffect(() => {
    const settling = setTimeout(() => setTerm(draft.trim()), SETTLE_MS)
    return () => clearTimeout(settling)
  }, [draft])

  useEffect(() => {
    if (!ready) return

    let current = true
    api
      .searchCatalog(type.mediaType, term)
      .then((found) => current && setAnswer({ asked, results: found }))
      .catch((err) =>
        current &&
        setFailure({
          asked,
          message: err instanceof ApiError ? err.message : 'Could not reach the server.',
        }),
      )

    // A slow answer to a term you have already typed past must not land on the new one.
    return () => {
      current = false
    }
  }, [asked, ready, term, type.mediaType])

  const close = () => {
    setOpen(false)
    setDraft('')
    setTerm('')
  }

  const resultsPath = () =>
    `/search?module=${module.slug}&type=${type.slug}&q=${encodeURIComponent(term)}`

  const viewAll = (event: FormEvent) => {
    event.preventDefault()
    if (!ready) return
    close()
    navigate(resultsPath())
  }

  // Only some modules have a page for a title nobody tracks yet. The rest have the full
  // results list, which is where such a title can be put on a shelf.
  const openResult = (result: SearchResult) => {
    const path = module.hasMediaPages
      ? `/media/${result.source}/${result.externalId}`
      : resultsPath()
    close()
    navigate(path)
  }

  return (
    <>
      <button
        ref={trigger}
        type="button"
        className="ghost icon-button"
        aria-label={`Search ${type.label}`}
        title={`Search ${type.label}`}
        aria-expanded={open}
        onClick={() => setOpen(true)}
      >
        <SearchIcon />
      </button>

      {open && (
        <div
          className="search-overlay"
          role="dialog"
          aria-modal="true"
          aria-label={`Search ${type.label}`}
          onPointerDown={(event) => {
            if (event.target === event.currentTarget) close()
          }}
        >
          <form className="search-overlay-panel" onSubmit={viewAll}>
            <span className="search-overlay-icon">
              <SearchIcon />
            </span>
            <input
              ref={input}
              type="search"
              value={draft}
              placeholder={type.searchPlaceholder}
              aria-label={`Search ${type.label}`}
              onChange={(event) => setDraft(event.target.value)}
            />
            {/* Clears the term, or leaves outright once there is nothing left to clear. */}
            <button
              type="button"
              className="ghost icon-button search-clear"
              aria-label={draft ? 'Clear search' : 'Close search'}
              onClick={() => {
                if (!draft) return close()
                setDraft('')
                input.current?.focus()
              }}
            >
              <ClearIcon />
            </button>
          </form>

          {!ready && (
            <p className="search-overlay-scope">
              Searching <strong>{type.label}</strong> in {module.label}
            </p>
          )}

          {ready && (
            <div className="search-results">
              {error && (
                <p className="alert" role="alert">
                  {error}
                </p>
              )}

              {searching && <p className="search-results-note">Searching…</p>}

              {results?.length === 0 && (
                <p className="search-results-note">Nothing found for “{term}”.</p>
              )}

              {results && results.length > 0 && (
                <>
                  <ul>
                    {results.slice(0, SHOWN).map((result) => (
                      <li key={keyOf(result)}>
                        <button type="button" onClick={() => openResult(result)}>
                          {result.coverUrl ? (
                            <img src={result.coverUrl} alt="" loading="lazy" />
                          ) : (
                            <span className="search-result-blank" aria-hidden />
                          )}
                          <span className="search-result-text">
                            <strong>{result.title}</strong>
                            <span className="muted">
                              {[yearOf(result), formatOf(result)].filter(Boolean).join(' ') ||
                                type.label}
                            </span>
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>

                  <button type="button" className="search-results-all" onClick={viewAll}>
                    View all {type.label.toLowerCase()} results
                  </button>
                </>
              )}
            </div>
          )}
        </div>
      )}
    </>
  )
}
