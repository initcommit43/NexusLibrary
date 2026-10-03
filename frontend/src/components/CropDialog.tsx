import { useRef, useState } from 'react'
import { createPortal } from 'react-dom'
import { errorMessage, type Framing } from '../api/client'
import { clamp, cropOf, cropWidths, framingFrom, type Crop } from './framing'
import { useEscapeKey } from './useEscapeKey'

type Corner = 'nw' | 'ne' | 'sw' | 'se'

const CORNERS: Corner[] = ['nw', 'ne', 'sw', 'se']

/** A nudge from the keyboard: a hundredth of the picture, or a twentieth with Shift held. */
const STEP = 0.01
const LEAP = 0.05

/** A crop laid over the picture, in shares of it. */
const placed = (crop: Crop) => ({
  left: `${crop.x * 100}%`,
  top: `${crop.y * 100}%`,
  width: `${crop.width * 100}%`,
  height: `${crop.height * 100}%`,
})

/** Where a drag took hold, and of what. */
type Hold = {
  corner: Corner | null
  pointerX: number
  pointerY: number
  from: Crop
  stage: DOMRect
}

/**
 * Choosing what part of a picture shows, on the whole picture at once.
 *
 * <p>The frame the picture ends up in shows only a part of it, so the dialog shows all of it
 * and marks that part — a circle for the avatar, the strip's own shape for the banner — with
 * the rest dimmed. Dragging inside moves the part shown; a corner handle resizes it, keeping
 * its shape. The arrow keys and plus and minus do the same for a keyboard.
 *
 * <p>Nothing about the picture's pixels is changed here. The crop is said back as the focus
 * point and zoom the frame is drawn with, which is all that is stored.
 */
export const CropDialog = ({
  title,
  src,
  shape,
  measureFrame,
  initial,
  confirmLabel,
  onConfirm,
  onClose,
}: {
  title: string
  src: string
  shape: 'circle' | 'strip'
  /**
   * The frame's shape, width over height, as it will actually be drawn. Asked when the picture
   * loads, so a strip that follows the window is measured as it stands when the dialog opens.
   */
  measureFrame: () => number
  initial: Framing
  confirmLabel: string
  onConfirm: (framing: Framing) => Promise<void>
  onClose: () => void
}) => {
  const [picture, setPicture] = useState<number | null>(null)
  const [frame, setFrame] = useState(1)
  const [crop, setCrop] = useState<Crop | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const stage = useRef<HTMLDivElement>(null)
  const hold = useRef<Hold | null>(null)
  // A drag that ends over the backdrop must not read as a click on it and close the dialog.
  const pressedBackdrop = useRef(false)

  useEscapeKey(onClose)

  const loaded = (image: HTMLImageElement) => {
    const shapeOf = image.naturalWidth / image.naturalHeight
    const framed = measureFrame()
    setPicture(shapeOf)
    setFrame(framed)
    setCrop(cropOf(initial, shapeOf, framed))
  }

  /** The crop at this width, its height following from the frame's shape, kept on the picture. */
  const sized = (width: number, x: number, y: number, held: number): Crop => {
    const height = (width * held) / frame
    return {
      x: clamp(x, 0, 1 - width),
      y: clamp(y, 0, 1 - height),
      width,
      height,
    }
  }

  const take = (event: React.PointerEvent, corner: Corner | null) => {
    if (!crop || !stage.current || busy) return
    event.stopPropagation()
    event.currentTarget.setPointerCapture(event.pointerId)
    hold.current = {
      corner,
      pointerX: event.clientX,
      pointerY: event.clientY,
      from: crop,
      stage: stage.current.getBoundingClientRect(),
    }
  }

  const drag = (event: React.PointerEvent) => {
    const held = hold.current
    if (!held || picture === null) return
    const { from, stage: box } = held

    if (held.corner === null) {
      setCrop(
        sized(
          from.width,
          from.x + (event.clientX - held.pointerX) / box.width,
          from.y + (event.clientY - held.pointerY) / box.height,
          picture,
        ),
      )
      return
    }

    // The opposite corner stays put; the one held follows the pointer, shape kept.
    const east = held.corner.endsWith('e')
    const south = held.corner.startsWith('s')
    const anchorX = east ? from.x : from.x + from.width
    const anchorY = south ? from.y : from.y + from.height
    const pointerX = (event.clientX - box.left) / box.width
    const pointerY = (event.clientY - box.top) / box.height

    const acrossHeight = (frame / picture) * Math.abs(pointerY - anchorY)
    const roomAcross = east ? 1 - anchorX : anchorX
    const roomDown = (frame / picture) * (south ? 1 - anchorY : anchorY)
    const { widest, narrowest } = cropWidths(picture, frame)
    const width = clamp(
      Math.max(Math.abs(pointerX - anchorX), acrossHeight),
      narrowest,
      Math.min(widest, roomAcross, roomDown),
    )
    const height = (width * picture) / frame

    setCrop({
      x: east ? anchorX : anchorX - width,
      y: south ? anchorY : anchorY - height,
      width,
      height,
    })
  }

  const release = () => {
    hold.current = null
  }

  const nudge = (event: React.KeyboardEvent) => {
    if (!crop || picture === null) return
    const step = event.shiftKey ? LEAP : STEP
    const { widest, narrowest } = cropWidths(picture, frame)
    const grow = (by: number) => {
      const width = clamp(crop.width * by, narrowest, widest)
      const height = (width * picture) / frame
      return sized(width, crop.x + (crop.width - width) / 2, crop.y + (crop.height - height) / 2, picture)
    }

    const moves: Record<string, () => Crop> = {
      ArrowLeft: () => sized(crop.width, crop.x - step, crop.y, picture),
      ArrowRight: () => sized(crop.width, crop.x + step, crop.y, picture),
      ArrowUp: () => sized(crop.width, crop.x, crop.y - step, picture),
      ArrowDown: () => sized(crop.width, crop.x, crop.y + step, picture),
      '+': () => grow(1 + step * 4),
      '=': () => grow(1 + step * 4),
      '-': () => grow(1 - step * 4),
    }
    const move = moves[event.key]
    if (!move) return
    event.preventDefault()
    setCrop(move())
  }

  const confirm = async () => {
    if (!crop || picture === null) return
    setBusy(true)
    setError(null)
    try {
      await onConfirm(framingFrom(crop, picture, frame))
      onClose()
    } catch (err) {
      setError(errorMessage(err, 'Could not save that.'))
      setBusy(false)
    }
  }

  // Into the body rather than where it is opened: the avatar and the banner sit in stacking
  // contexts of their own, which would hold a fixed backdrop under the page's sticky header.
  return createPortal(
    <div
      className="dialog-backdrop"
      role="presentation"
      onPointerDown={(event) => {
        pressedBackdrop.current = event.target === event.currentTarget
      }}
      onClick={(event) => {
        if (pressedBackdrop.current && event.target === event.currentTarget) onClose()
      }}
    >
      <div
        className={shape === 'strip' ? 'dialog crop-dialog is-strip' : 'dialog crop-dialog'}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header className="dialog-head">
          <h2>{title}</h2>
          <button type="button" className="ghost icon-button" aria-label="Close" onClick={onClose}>
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
              <path d="M6 6l12 12M18 6L6 18" strokeWidth="1.8" strokeLinecap="round" />
            </svg>
          </button>
        </header>

        <div className="crop-holder">
          <div className="crop-stage" ref={stage}>
            <img src={src} alt="" draggable={false} onLoad={(event) => loaded(event.currentTarget)} />

            {/* The shade clips to the picture; the handles above it may overhang its edge. */}
            {crop && (
              <div className="crop-shade" aria-hidden="true">
                <div
                  className={shape === 'circle' ? 'crop-cutout is-circle' : 'crop-cutout'}
                  style={placed(crop)}
                />
              </div>
            )}

            {crop && (
              <div
                className={shape === 'circle' ? 'crop-area is-circle' : 'crop-area'}
                style={placed(crop)}
                tabIndex={0}
                role="group"
                aria-label="Visible area. Drag or use the arrow keys to move it; drag a corner or press plus and minus to resize it."
                onPointerDown={(event) => take(event, null)}
                onPointerMove={drag}
                onPointerUp={release}
                onPointerCancel={release}
                onKeyDown={nudge}
              >
                {CORNERS.map((corner) => (
                  <span
                    key={corner}
                    className={`crop-handle is-${corner}`}
                    aria-hidden="true"
                    onPointerDown={(event) => take(event, corner)}
                    onPointerMove={drag}
                    onPointerUp={release}
                    onPointerCancel={release}
                  />
                ))}
              </div>
            )}
          </div>
        </div>

        {error && (
          <p className="alert" role="alert">
            {error}
          </p>
        )}

        <button type="button" className="crop-confirm" disabled={!crop || busy} onClick={() => void confirm()}>
          {busy ? 'Saving…' : confirmLabel}
        </button>
      </div>
    </div>,
    document.body,
  )
}
