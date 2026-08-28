import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { ApiError, api, type SearchResult } from '../api/client'
import { keyOf } from './useTrackable'
import { mediaPathFor } from '../modules/registry'
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
 * Catalogue search for the module you are standing in.
 *
 * <p>An overlay rather than a field in the header: it matters only for the few seconds you
 * are typing into it, and the header already spends its width on the shelves of whichever
 * module you are in. Scoping it to the module is the point — the app knows you are looking
 * at anime, so it does not ask.
 *
 * <p>Every media type the module owns is searched at once and answered in its own card, so
 * a name shared by an anime and its manga comes back as both rather than as whichever shelf
 * you happened to be on.
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

  // Answers are filed under the question they answer, so a stale or emptied term shows
  // nothing rather than the last thing that came back.
  const [answers, setAnswers] = useState<Record<string, SearchResult[]>>({})
  const [failures, setFailures] = useState<Record<string, string>>({})

  const ready = term.length >= MIN_TERM
  const askedFor = (media: string) => `${media}:${term}`

  const cards = module.types.map((shelf) => ({
    shelf,
    results: answers[askedFor(shelf.mediaType)] ?? null,
    error: failures[askedFor(shelf.mediaType)] ?? null,
  }))

  const searching = ready && cards.some((card) => card.results === null && card.error === null)
  const found = cards.filter((card) => card.results && card.results.length > 0)
  const error = cards.find((card) => card.error)?.error ?? null

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

  // The page behind is not what the wheel is for while this is up: scrolling it moves the
  // results out from under the pointer and leaves the reader somewhere they did not choose.
  useEffect(() => {
    if (!open) return

    document.body.classList.add('scroll-locked')
    return () => document.body.classList.remove('scroll-locked')
  }, [open])

  // A pause in the typing is the question; every keystroke before it was a guess at one.
  useEffect(() => {
    const settling = setTimeout(() => setTerm(draft.trim()), SETTLE_MS)
    return () => clearTimeout(settling)
  }, [draft])

  useEffect(() => {
    if (!ready) return

    let current = true
    for (const shelf of module.types) {
      const asked = `${shelf.mediaType}:${term}`
      api
        .searchCatalog(shelf.mediaType, term)
        .then(
          (hits) => current && setAnswers((held) => ({ ...held, [asked]: hits })),
        )
        .catch(
          (err) =>
            current &&
            setFailures((held) => ({
              ...held,
              [asked]: err instanceof ApiError ? err.message : 'Could not reach the server.',
            })),
        )
    }

    // Slow answers to a term you have already typed past must not land on the new one.
    return () => {
      current = false
    }
  }, [module, ready, term])

  const close = () => {
    setOpen(false)
    setDraft('')
    setTerm('')
  }

  const resultsPath = (shelf: MediaTypeDefinition) =>
    `/search?module=${module.slug}&type=${shelf.slug}&q=${encodeURIComponent(term)}`

  const goTo = (shelf: MediaTypeDefinition) => {
    if (!ready) return
    close()
    navigate(resultsPath(shelf))
  }

  // Enter takes the shelf you were already on; the cards each offer their own.
  const submit = (event: FormEvent) => {
    event.preventDefault()
    goTo(type)
  }

  const openResult = (result: SearchResult) => {
    close()
    navigate(mediaPathFor(result))
  }

  return (
    <>
      <button
        ref={trigger}
        type="button"
        className="ghost icon-button"
        aria-label={`Search ${module.label}`}
        title={`Search ${module.label}`}
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
          aria-label={`Search ${module.label}`}
          onPointerDown={(event) => {
            if (event.target === event.currentTarget) close()
          }}
        >
          <form className="search-overlay-panel" onSubmit={submit}>
            <span className="search-overlay-icon">
              <SearchIcon />
            </span>
            <input
              ref={input}
              type="search"
              value={draft}
              placeholder={type.searchPlaceholder}
              aria-label={`Search ${module.label}`}
              onChange={(event) => setDraft(event.target.value)}
            />
            {/* Clears the term, or leaves outright once there is nothing left to clear. */}
            <button
              type="button"
              className="icon-button search-clear"
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

          {ready && (
            <div className="search-results">
              {error && (
                <p className="alert" role="alert">
                  {error}
                </p>
              )}

              {searching && found.length === 0 && (
                <p className="search-results-note">Searching…</p>
              )}

              {!searching && !error && found.length === 0 && (
                <p className="search-results-note">Nothing found for “{term}”.</p>
              )}

              {found.map(({ shelf, results }) => (
                <section className="search-card" key={shelf.slug}>
                  <h2>{shelf.label}</h2>

                  <ul>
                    {results?.slice(0, SHOWN).map((result) => (
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
                                shelf.label}
                            </span>
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>

                  <button
                    type="button"
                    className="search-results-all"
                    onClick={() => goTo(shelf)}
                  >
                    View all {shelf.label.toLowerCase()} results
                  </button>
                </section>
              ))}
            </div>
          )}
        </div>
      )}
    </>
  )
}
