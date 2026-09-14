import { useState } from 'react'
import { api, type ProfileBanner } from '../api/client'
import { CLOSEST, COVER, framedStyle, framingOf } from './framing'
import { useFramingEditor } from './useFramingEditor'

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
 * The banner, and the handling of where the picture sits inside it.
 *
 * <p>A strip five times wider than it is tall and a sixteen-by-nine screenshot are not the
 * same shape, so a plain cover crop takes a band out of the middle and throws away whatever
 * the reader picked the picture for. Dragging says which band; the slider says how close. The
 * editing itself is {@link useFramingEditor}, shared with the avatar circle.
 *
 * <p>What is this frame's own is the hint: where nothing is hidden in an axis, that axis does
 * not move — there is nowhere for it to go, and a line telling the reader to drag is a control
 * that reads as broken.
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
  const { framing, error, busy, image, setFraming, dragHandlers, commit } = useFramingEditor({
    session: `${banner.imageUrl}#${adjusting}`,
    stored: () => framingOf(banner),
    adjusting,
    save: api.frameProfileBanner,
    onFramed,
    onClose,
  })

  /*
   * Both shapes, as width over height, taken when the picture loads. Which of them is the
   * wider decides which way a cover crop hides anything at all: a picture wider in
   * proportion than the strip overflows sideways and is already showing its full height,
   * so there is nothing above or below to pull into view until it is zoomed.
   */
  const [shapes, setShapes] = useState<{ picture: number; strip: number } | null>(null)

  const zoomed = framing.zoom > COVER
  const canMoveDown = zoomed || (shapes !== null && shapes.picture < shapes.strip)
  const canMoveAcross = zoomed || (shapes !== null && shapes.picture > shapes.strip)

  return (
    <div className={adjusting ? 'profile-banner adjusting' : 'profile-banner'}>
      <img
        ref={image}
        src={banner.imageUrl}
        alt=""
        draggable={false}
        style={framedStyle(framing)}
        onLoad={(event) =>
          setShapes({
            picture: event.currentTarget.naturalWidth / event.currentTarget.naturalHeight,
            strip: event.currentTarget.offsetWidth / event.currentTarget.offsetHeight,
          })
        }
        {...dragHandlers}
      />

      {/*
        * In the corner of the picture, out of sight until it is reached for. A disc behind
        * it because the ground here is whatever art the reader chose: a bare glyph is
        * legible over a night sky and gone over a snowfield.
        */}
      {!adjusting && (
        <button
          type="button"
          className="banner-adjust-button"
          aria-label="Adjust how the banner sits"
          title="Adjust"
          onClick={onAdjust}
        >
          <ResizeIcon />
        </button>
      )}

      {adjusting && (
        <div className="banner-adjust">
          {/*
            * What the drag can actually do here, rather than a line that says "drag" over a
            * picture with nowhere to go: a banner the same shape as the strip is already
            * showing all of itself, and the slider is what makes room.
            */}
          <span className="banner-adjust-hint">
            {error ??
              (canMoveDown && canMoveAcross
                ? 'Drag the picture to move it'
                : canMoveDown
                  ? 'Drag the picture up and down'
                  : canMoveAcross
                    ? 'Drag the picture left and right'
                    : 'Zoom in to move this picture')}
          </span>

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
