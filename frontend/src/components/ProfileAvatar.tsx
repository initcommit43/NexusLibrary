import { api, type ProfilePicture } from '../api/client'
import { CLOSEST, COVER, framedStyle, framingOf } from './framing'
import { useFramingEditor } from './useFramingEditor'

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
 * close. The same maths and the same editing the banner strip uses — {@link useFramingEditor}
 * — against a different shape.
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
  const { framing, error, busy, image, setFraming, dragHandlers, commit } = useFramingEditor({
    session: `${picture?.imageUrl ?? ''}#${adjusting}`,
    // A profile with no picture yet still has a framing to open on: dead centre, uncropped.
    stored: () => (picture ? framingOf(picture) : { focusX: 50, focusY: 50, zoom: COVER }),
    adjusting,
    save: api.frameProfilePicture,
    onFramed,
    onClose,
  })

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
            {...dragHandlers}
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
          <button type="button" className="small" disabled={busy} onClick={() => void commit()}>
            Save
          </button>
        </div>
      )}
    </div>
  )
}
