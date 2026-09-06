import { useEffect, useRef, useState } from 'react'
import { ApiError, api, type Framing, type ProfileBanner } from '../api/client'
import {
  CLOSEST,
  COVER,
  type DragOrigin,
  draggedTo,
  framedStyle,
  framingOf,
  hidden,
} from './framing'

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
 * the reader picked the picture for. Dragging says which band; the slider says how close.
 *
 * <p>The drag is measured against how much of the image is actually hidden, so the picture
 * keeps pace with the pointer rather than racing it on a tall image and crawling on a wide
 * one. Where nothing is hidden in an axis, that axis does not move — there is nowhere for
 * it to go, and a control that pretends otherwise reads as broken.
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
  /*
   * A different picture, or an adjustment left rather than saved: either way the strip goes
   * back to what is stored, which is what makes closing the bar a cancel. Kept as the
   * session an edit belongs to rather than reset in an effect, so leaving adjust mode is
   * one render rather than a render and a correction.
   */
  const session = `${banner.imageUrl}#${adjusting}`

  const [edit, setEdit] = useState(() => ({
    session,
    framing: framingOf(banner),
    error: null as string | null,
  }))

  const held = edit.session === session
    ? edit
    : { session, framing: framingOf(banner), error: null }

  const framing = held.framing
  const error = held.error

  const setFraming = (next: Framing) => setEdit({ ...held, framing: next })

  const [busy, setBusy] = useState(false)

  /*
   * Both shapes, as width over height, taken when the picture loads. Which of them is the
   * wider decides which way a cover crop hides anything at all: a picture wider in
   * proportion than the strip overflows sideways and is already showing its full height,
   * so there is nothing above or below to pull into view until it is zoomed.
   */
  const [shapes, setShapes] = useState<{ picture: number; strip: number } | null>(null)

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

  const zoomed = framing.zoom > COVER
  const canMoveDown = zoomed || (shapes !== null && shapes.picture < shapes.strip)
  const canMoveAcross = zoomed || (shapes !== null && shapes.picture > shapes.strip)

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
        await api.frameProfileBanner({
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
      // Cleared on the way out however it went: the banner stays on the page after a save,
      // so a flag left set here is a Save button that never comes back.
      setBusy(false)
    }
  }

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
        onPointerDown={startDrag}
        onPointerMove={moveDrag}
        onPointerUp={endDrag}
        onPointerCancel={endDrag}
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
          <button type="button" className="small" disabled={busy} onClick={() => void save()}>
            Save
          </button>
        </div>
      )}
    </div>
  )
}
