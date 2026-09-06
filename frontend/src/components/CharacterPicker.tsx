import { useEffect, useMemo, useState } from 'react'
import { ApiError, api, type ProfilePicture, type TrackedItem } from '../api/client'
import { readCharacters, type CharacterRole } from './mediaDetail'

/*
 * Only AniList names characters. A film has a cast — real actors, credited with the part they
 * played — and a game or a book has neither, so nothing outside AniList is offered here.
 */
const HAS_CHARACTERS = 'ANILIST'

/**
 * A library of hundreds is not a grid to scroll through looking for one title. The most
 * recently touched come first, which is where a reader's mind already is, and the search
 * narrows to anything older.
 */
const SHOWN = 60

/**
 * Choosing the face at the head of the profile, from a title in the reader's own library.
 *
 * <p>Two steps rather than the banner's one, because a character is a level deeper than an
 * entry: the banner is wide art the title itself has, while a character lives inside that
 * title's detail and there are a dozen of them. So the reader picks the title they have in
 * mind, and only that title's detail is fetched — the same fetch opening its page would make,
 * against the same shared cache.
 */
export const CharacterPicker = ({
  entries,
  chosen,
  onChosen,
  onCleared,
  onClose,
}: {
  entries: TrackedItem[]
  chosen: ProfilePicture | null
  onChosen: (picture: ProfilePicture) => void
  onCleared: () => void
  onClose: () => void
}) => {
  const [query, setQuery] = useState('')
  const [opened, setOpened] = useState<TrackedItem | null>(null)
  const [characters, setCharacters] = useState<CharacterRole[] | null>(null)
  const [busy, setBusy] = useState<string | null>(null)
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
        entry.source === HAS_CHARACTERS &&
        (needle === '' || entry.title.toLowerCase().includes(needle)),
    )
  }, [entries, query])

  /**
   * Opening a title fetches its detail and reads the characters out of it — the same call
   * and the same reader the title's own page uses, so nothing here learns AniList's shapes.
   */
  const open = async (entry: TrackedItem) => {
    setOpened(entry)
    setCharacters(null)
    setError(null)
    try {
      const media = await api.media(entry.source, entry.externalId)
      setCharacters(readCharacters((media.metadata.detail ?? {}) as Record<string, unknown>))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load that title.')
      setCharacters([])
    }
  }

  const back = () => {
    setOpened(null)
    setCharacters(null)
    setError(null)
  }

  const choose = async (entry: TrackedItem, characterId: string) => {
    setBusy(characterId)
    setError(null)
    try {
      onChosen(await api.chooseProfilePicture(entry.id, characterId))
      onClose()
    } catch (err) {
      // A character the title turns out not to have answers plainly, and the picker stays
      // open on the message: the next thing the reader does is pick a different one.
      setError(err instanceof ApiError ? err.message : 'Could not use that as a picture.')
      setBusy(null)
    }
  }

  const clear = async () => {
    setBusy(null)
    setError(null)
    try {
      await api.clearProfilePicture()
      onCleared()
      onClose()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove the picture.')
    }
  }

  return (
    // Clicking the backdrop closes; clicking inside must not, hence the stopped propagation.
    <div className="dialog-backdrop" onClick={onClose} role="presentation">
      <div
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-label="Choose a profile picture"
        onClick={(event) => event.stopPropagation()}
      >
        <header className="dialog-head">
          <h2>{opened ? opened.title : 'Choose a character'}</h2>
          <button type="button" className="ghost icon-button" aria-label="Close" onClick={onClose}>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
              <path d="M6 6l12 12M18 6L6 18" strokeWidth="1.8" strokeLinecap="round" />
            </svg>
          </button>
        </header>

        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}

        {opened ? (
          <>
            <button type="button" className="ghost small character-back" onClick={back}>
              ← All titles
            </button>

            {characters === null ? (
              <p className="muted">Loading characters…</p>
            ) : characters.length === 0 ? (
              <p className="muted">This title has no characters to take a picture from.</p>
            ) : (
              <ul className="character-picker">
                {characters.map(({ character }) => (
                  <li key={character.id}>
                    <button
                      type="button"
                      className="character-option"
                      disabled={busy !== null}
                      aria-busy={busy === character.id}
                      title={character.name}
                      onClick={() => void choose(opened, character.id)}
                    >
                      {character.image ? (
                        <img src={character.image} alt="" loading="lazy" />
                      ) : (
                        <span className="cover-placeholder" aria-hidden="true" />
                      )}
                      <span className="character-option-name">{character.name}</span>
                      {character.role && <span className="muted small">{character.role}</span>}
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </>
        ) : (
          <>
            <label className="field">
              <span>Search your anime and manga</span>
              <input
                type="search"
                value={query}
                autoFocus
                placeholder="Title"
                onChange={(event) => setQuery(event.target.value)}
              />
            </label>

            {matching.length === 0 ? (
              <p className="muted">Nothing here matches that.</p>
            ) : (
              <ul className="banner-picker">
                {matching.slice(0, SHOWN).map((entry) => (
                  <li key={entry.id}>
                    <button
                      type="button"
                      className="banner-option"
                      title={entry.title}
                      onClick={() => void open(entry)}
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
          </>
        )}

        {chosen && (
          <div className="dialog-foot">
            <button type="button" className="ghost danger" onClick={() => void clear()}>
              Remove picture
            </button>
          </div>
        )}
      </div>
    </div>
  )
}
