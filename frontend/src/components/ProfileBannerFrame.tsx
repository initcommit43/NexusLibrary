import { useRef } from 'react'
import { api, type ProfileBanner } from '../api/client'
import { CropDialog } from './CropDialog'
import { framedStyle, framingOf } from './framing'

/** Arrows to opposite corners: the gesture for taking hold of a picture and sizing it. */
const ResizeIcon = () => (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" aria-hidden>
    <path
      d="M14 4h6v6M20 4l-7 7M10 20H4v-6M4 20l7-7"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
)

/**
 * The banner, and the way into placing the picture inside it.
 *
 * <p>A strip several times wider than it is tall and a sixteen-by-nine screenshot are not the
 * same shape, so a plain cover crop takes a band out of the middle and throws away whatever
 * the reader picked the picture for. The crop dialog shows the whole picture with the strip's
 * own shape marked on it — taken from the strip as it is drawn on this screen, so what is
 * marked is what will show.
 */
export const ProfileBannerFrame = ({
  banner,
  adjusting,
  onAdjust,
  onFramed,
  onClose,
}: {
  banner: ProfileBanner
  adjusting: boolean
  onAdjust: () => void
  onFramed: (framed: ProfileBanner) => void
  onClose: () => void
}) => {
  const strip = useRef<HTMLDivElement>(null)

  return (
    <div className="profile-banner" ref={strip}>
      <img src={banner.imageUrl} alt="" draggable={false} style={framedStyle(framingOf(banner))} />

      {/*
        * In the corner of the picture, out of sight until it is reached for. A disc behind
        * it because the ground here is whatever art the reader chose: a bare glyph is
        * legible over a night sky and gone over a snowfield.
        */}
      <button
        type="button"
        className="banner-adjust-button"
        aria-label="Adjust how the banner sits"
        title="Adjust"
        onClick={onAdjust}
      >
        <ResizeIcon />
      </button>

      {adjusting && (
        <CropDialog
          title="Adjust your banner"
          src={banner.imageUrl}
          shape="strip"
          measureFrame={() => (strip.current ? strip.current.offsetWidth / strip.current.offsetHeight : 5)}
          initial={framingOf(banner)}
          confirmLabel="Save banner"
          onConfirm={async (framing) => onFramed(await api.frameProfileBanner(framing))}
          onClose={onClose}
        />
      )}
    </div>
  )
}
