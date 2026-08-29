import { useEffect, useMemo, useState } from 'react'
import { ApiError, api, type ProfileBanner, type TrackedItem } from '../api/client'

/*
 * Open Library has no wide art of any kind, so a book cannot supply a banner and is not
 * offered as one. Every other source has something: AniList names a banner outright, a film
 * has its stills, a game its screenshots.
 */
const NO_WIDE_ART = 'OPEN_LIBRARY'

/**
 * A library of hundreds is not a grid to scroll through looking for one title. The most
 * recently touched come first, which is where a reader's mind already is, and the search
 * narrows to anything older.
 */
const SHOWN = 60

/**
 * Choosing the picture at the head of the profile, from the reader's own library.
 *
 * <p>Covers rather than a list of names: the reader is choosing an image, and the cover is
 * the only thing here that hints at what the banner behind it will look like. What it
 * actually looks like arrives when it is chosen — the wide art lives in a title's detail,
 * and fetching it for six hundred covers to fill a picker is not a trade worth making.
 */
export const BannerPicker = ({
  entries,
  chosen,
  onChosen,
  onCleared,
  onClose,
}: {
  entries: TrackedItem[]
  chosen: ProfileBanner | null
  onChosen: (banner: ProfileBanner) => void
  onCleared: () => void
  onClose: () => void
}) => {
  const [query, setQuery] = useState('')
  const [busy, setBusy] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  const matching = useMemo(() => {
    const needle = query.trim().toLowerCase()
    return entries.filter(
      (entry) =>
        entry.source !== NO_WIDE_ART &&
        (needle === '' || entry.title.toLowerCase().includes(needle)),
    )
  }, [entries, query])

  const choose = async (entry: TrackedItem) => {
    setBusy(entry.id)
    setError(null)
    try {
      onChosen(await api.chooseProfileBanner(entry.id))
      onClose()
    } catch (err) {
      // A title whose source has no banner for it answers plainly, and the picker stays
      // open on the message: the next thing the reader does is pick a different one.
      setError(err instanceof ApiError ? err.message : 'Could not use that as a banner.')
      setBusy(null)
    }
  }

  const clear = async () => {
    setBusy(null)
    setError(null)
    try {
      await api.clearProfileBanner()
      onCleared()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove the banner.')
    }
  }

  return (
    // Clicking the backdrop closes; clicking inside must not, hence the stopped propagation.
    <div className="dialog-backdrop" onClick={onClose} role="presentation">
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-label="Choose a profile banner"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="dialog-head">
          <h2>Choose a banner</h2>
          <button type="button" className="ghost icon-button" aria-label="Close" onClick={onClose}>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
              <path d="M6 6l12 12M18 6L6 18" strokeWidth="1.8" strokeLinecap="round" />
            </svg>
          </button>
        </header>

        <label className="field">
          <span>Search your library</span>
          <input
            type="search"
            value={query}
            autoFocus
            placeholder="Title"
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>

        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}

        {matching.length === 0 ? (
          <p className="muted">Nothing here matches that.</p>
        ) : (
          <ul className="banner-picker">
            {matching.slice(0, SHOWN).map((entry) => (
              <li key={entry.id}>
                <button
                  type="button"
                  className="banner-option"
                  disabled={busy !== null}
                  aria-busy={busy === entry.id}
                  title={entry.title}
                  onClick={() => void choose(entry)}
                >
                  {entry.coverUrl ? (
                    <img src={entry.coverUrl} alt="" loading="lazy" />
                  ) : (
                    <span className="cover-placeholder" aria-hidden="true" />
                  )}
                  <span className="banner-option-title">{entry.title}</span>
                </button>
              </li>
            ))}
          </ul>
        )}

        {matching.length > SHOWN && (
          <p className="muted">
            {(matching.length - SHOWN).toLocaleString()} more match; search to narrow them.
          </p>
        )}

        {chosen && (
          <div className="dialog-foot">
            <button type="button" className="ghost danger" onClick={() => void clear()}>
              Remove banner
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
