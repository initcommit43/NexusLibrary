import { useEffect, useRef, useState } from 'react'
import { ApiError, api, type Framing, type ProfilePicture } from '../api/client'
import {
  CLOSEST,
  COVER,
  type DragOrigin,
  draggedTo,
  framedStyle,
  framingOf,
  hidden,
} from './framing'

/** A head and shoulders: what a profile has before the reader gives it a face. */
const PersonIcon = () => (
  <svg viewBox="0 0 24 24" width="40" height="40" fill="none" stroke="currentColor" aria-hidden>
    <circle cx="12" cy="8.5" r="3.75" strokeWidth="1.5" />
    <path d="M4.5 20a7.5 7.5 0 0 1 15 0" strokeWidth="1.5" strokeLinecap="round" />
  </svg>
)

/** The pencil every editable thing wears, kept to the one stroke that reads at this size. */
const PencilIcon = () => (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" aria-hidden>
    <path
      d="M4 20h4L19 9a2.1 2.1 0 0 0-3-3L5 17v3z"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
)

/**
 * The circle at the head of a profile, and the handling of where the face sits inside it.
 *
 * <p>Character art is a tall portrait and this is a circle, so a plain cover crop lands on a
 * torso about as often as on a face. Dragging says which part to hold; the slider says how
 * close. The same maths the banner strip uses, against a different shape.
 *
 * <p>One affordance at rest: a pencil that fades in over the picture and opens the picker.
 * Choosing there drops straight back here in adjust mode, which is why cropping is not a
 * second thing to go looking for.
 */
export const ProfileAvatar = ({
  picture,
  adjusting,
  onEdit,
  onFramed,
  onClose,
}: {
  picture: ProfilePicture | null
  adjusting: boolean
  onEdit: () => void
  onFramed: (framed: ProfilePicture) => void
  onClose: () => void
}) => {
  /*
   * A different face, or an adjustment left rather than saved: either way the circle goes back
   * to what is stored, which is what makes closing the bar a cancel.
   */
  const session = `${picture?.imageUrl ?? ''}#${adjusting}`

  const [edit, setEdit] = useState(() => ({
    session,
    framing: picture ? framingOf(picture) : { focusX: 50, focusY: 50, zoom: COVER },
    error: null as string | null,
  }))

  const held =
    edit.session === session
      ? edit
      : {
          session,
          framing: picture ? framingOf(picture) : { focusX: 50, focusY: 50, zoom: COVER },
          error: null,
        }

  const framing = held.framing
  const error = held.error

  const setFraming = (next: Framing) => setEdit({ ...held, framing: next })

  const [busy, setBusy] = useState(false)

  const image = useRef<HTMLImageElement>(null)
  const drag = useRef<DragOrigin | null>(null)

  useEffect(() => {
    if (!adjusting) return

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [adjusting, onClose])

  const startDrag = (event: React.PointerEvent<HTMLImageElement>) => {
    if (!adjusting) return
    event.currentTarget.setPointerCapture(event.pointerId)
    drag.current = { x: event.clientX, y: event.clientY, from: framing }
  }

  const moveDrag = (event: React.PointerEvent<HTMLImageElement>) => {
    const from = drag.current
    const room = hidden(image.current, framing.zoom)
    if (!from || !room) return

    setFraming(draggedTo(from, room, event.clientX, event.clientY))
  }

  const endDrag = () => {
    drag.current = null
  }

  const save = async () => {
    setBusy(true)
    setEdit({ ...held, error: null })
    try {
      onFramed(
        await api.frameProfilePicture({
          focusX: Math.round(framing.focusX),
          focusY: Math.round(framing.focusY),
          zoom: Math.round(framing.zoom),
        }),
      )
      onClose()
    } catch (err) {
      setEdit({
        ...held,
        error: err instanceof ApiError ? err.message : 'Could not save that.',
      })
    } finally {
      // Cleared on the way out however it went: the picture stays on the page after a save,
      // so a flag left set here is a Save button that never comes back.
      setBusy(false)
    }
  }

  return (
    <div className="profile-avatar-holder">
      <div className={adjusting ? 'profile-avatar adjusting' : 'profile-avatar'}>
        {picture ? (
          <img
            ref={image}
            src={picture.imageUrl}
            alt={picture.characterName}
            title={`${picture.characterName} — ${picture.title}`}
            draggable={false}
            style={framedStyle(framing)}
            onPointerDown={startDrag}
            onPointerMove={moveDrag}
            onPointerUp={endDrag}
            onPointerCancel={endDrag}
          />
        ) : (
          <PersonIcon />
        )}

        {/*
          * Out of sight until the circle is reached for, like the fold control on home: it
          * belongs on the thing it changes, but a profile is not read for its portrait. A disc
          * behind it because the ground here is whatever art the reader chose.
          */}
        {!adjusting && (
          <button
            type="button"
            className="avatar-edit-button"
            aria-label={picture ? 'Change your profile picture' : 'Choose a profile picture'}
            title={picture ? 'Change picture' : 'Add a picture'}
            onClick={onEdit}
          >
            <PencilIcon />
          </button>
        )}
      </div>

      {adjusting && picture && (
        <div className="avatar-adjust">
          <span className="avatar-adjust-hint">{error ?? 'Drag the picture to move it'}</span>

          <input
            type="range"
            min={COVER}
            max={CLOSEST}
            value={framing.zoom}
            aria-label="Zoom"
            onChange={(event) => setFraming({ ...framing, zoom: Number(event.target.value) })}
          />

          <button type="button" className="ghost small" disabled={busy} onClick={onClose}>
            Cancel
          </button>
          <button type="button" className="small" disabled={busy} onClick={() => void save()}>
            Save
          </button>
        </div>
      )}
    </div>
  )
}
