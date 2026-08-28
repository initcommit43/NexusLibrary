import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import type { MediaTypeDefinition, ModuleDefinition } from '../modules/registry'

const SearchIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <circle cx="11" cy="11" r="7" strokeWidth="1.8" />
    <path d="m20 20-3.5-3.5" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
)

/**
 * Catalogue search for the shelf you are standing on.
 *
 * <p>An overlay rather than a field in the header: it matters only for the few seconds you
 * are typing into it, and the header already spends its width on the shelves of whichever
 * module you are in. Scoping it to the current media type is the point — the app knows you
 * are looking at games, so it does not ask.
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
  const input = useRef<HTMLInputElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  const navigate = useNavigate()

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

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const query = draft.trim()
    if (!query) return

    setOpen(false)
    setDraft('')
    navigate(
      `/search?module=${module.slug}&type=${type.slug}&q=${encodeURIComponent(query)}`,
    )
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
            if (event.target === event.currentTarget) setOpen(false)
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
              aria-label={`Search ${type.label}`}
              onChange={(event) => setDraft(event.target.value)}
            />
            <button type="submit" disabled={!draft.trim()}>
              Search
            </button>
          </form>

          {/* Says what it will search, because the field only ever covers one media type. */}
          <p className="search-overlay-scope">
            Searching <strong>{type.label}</strong> in {module.label}
          </p>
        </div>
      )}
    </>
  )
}
