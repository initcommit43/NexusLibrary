import { useRef, useState } from 'react'
import { api, errorMessage, type Framing, type ProfilePicture } from '../api/client'
import { CropDialog } from './CropDialog'
import { COVER, framedStyle, framingOf } from './framing'
import { PictureFileError, preparePicture } from './pictureFile'
import { useUploadedSrc } from './ownPicture'
import { useMenuDismiss } from './useMenuDismiss'

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

const CENTRED: Framing = { focusX: 50, focusY: 50, zoom: COVER }

/** What the crop dialog is open on: a file about to be sent, or the picture already held. */
type Cropping = { kind: 'new'; picture: Blob; src: string } | { kind: 'adjust'; src: string }

/**
 * The circle at the head of a profile, and the way to change what is in it.
 *
 * <p>The pencil opens a short menu once there is a picture — a new one, the crop of this one,
 * or none — and goes straight to choosing a file while there is not, since then that is the
 * only thing it could mean. Either way the crop dialog is where the picture is placed: it shows
 * the whole file with the circle marked on it, rather than asking for a drag inside a circle
 * too small to see what is outside it.
 */
export const ProfileAvatar = ({
  picture,
  onChanged,
  onError,
}: {
  picture: ProfilePicture | null
  onChanged: (picture: ProfilePicture | null) => void
  onError: (message: string) => void
}) => {
  const uploaded = useUploadedSrc(picture?.version ?? null)
  const src = picture?.version ? uploaded : (picture?.imageUrl ?? null)

  const { open, setOpen, container, trigger } = useMenuDismiss<HTMLDivElement, HTMLButtonElement>()
  const chooser = useRef<HTMLInputElement>(null)
  const [cropping, setCropping] = useState<Cropping | null>(null)

  const choose = () => {
    setOpen(false)
    chooser.current?.click()
  }

  const chosen = async (file: File | undefined) => {
    if (!file) return
    try {
      const prepared = await preparePicture(file)
      setCropping({ kind: 'new', picture: prepared, src: URL.createObjectURL(prepared) })
    } catch (err) {
      onError(err instanceof PictureFileError ? err.message : 'That file could not be read as a picture.')
    }
  }

  const closeCrop = () => {
    if (cropping?.kind === 'new') URL.revokeObjectURL(cropping.src)
    setCropping(null)
  }

  const remove = async () => {
    setOpen(false)
    try {
      await api.clearProfilePicture()
      onChanged(null)
    } catch (err) {
      onError(errorMessage(err, 'Could not remove the picture.'))
    }
  }

  return (
    <div className="profile-avatar-holder" ref={container}>
      <div className="profile-avatar">
        {src && picture ? (
          <img src={src} alt="Your profile picture" draggable={false} style={framedStyle(framingOf(picture))} />
        ) : (
          <PersonIcon />
        )}

        {/*
          * Out of sight until the circle is reached for, like the fold control on home: it
          * belongs on the thing it changes, but a profile is not read for its portrait.
          */}
        <button
          ref={trigger}
          type="button"
          className="avatar-edit-button"
          aria-label={picture ? 'Change your profile picture' : 'Upload a profile picture'}
          aria-haspopup={picture ? 'menu' : undefined}
          aria-expanded={picture ? open : undefined}
          title={picture ? 'Change picture' : 'Upload a picture'}
          onClick={() => (picture ? setOpen((wasOpen) => !wasOpen) : choose())}
        >
          <PencilIcon />
        </button>
      </div>

      {open && picture && (
        <ul className="avatar-menu" role="menu">
          <li role="none">
            <button type="button" role="menuitem" onClick={choose}>
              Upload
            </button>
          </li>
          {src && (
            <li role="none">
              <button
                type="button"
                role="menuitem"
                onClick={() => {
                  setOpen(false)
                  setCropping({ kind: 'adjust', src })
                }}
              >
                Adjust
              </button>
            </li>
          )}
          <li role="none">
            {/* Not dressed as a danger: a picture is put back in two clicks, nothing is lost. */}
            <button type="button" role="menuitem" onClick={() => void remove()}>
              Remove
            </button>
          </li>
        </ul>
      )}

      <input
        ref={chooser}
        type="file"
        accept="image/jpeg,image/png"
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0]
          // Cleared at once, so choosing the same file again after a cancel still registers.
          event.target.value = ''
          void chosen(file)
        }}
      />

      {cropping && (
        <CropDialog
          title={cropping.kind === 'new' ? 'Crop your new profile picture' : 'Adjust your profile picture'}
          src={cropping.src}
          shape="circle"
          measureFrame={() => 1}
          initial={cropping.kind === 'new' || !picture ? CENTRED : framingOf(picture)}
          confirmLabel={cropping.kind === 'new' ? 'Set new profile picture' : 'Save'}
          onConfirm={async (framing) => {
            onChanged(
              cropping.kind === 'new'
                ? await api.uploadProfilePicture(cropping.picture, framing)
                : await api.frameProfilePicture(framing),
            )
          }}
          onClose={closeCrop}
        />
      )}
    </div>
  )
}
