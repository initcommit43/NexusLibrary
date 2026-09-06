/**
 * Holding a picture inside a frame that is not its shape.
 *
 * <p>A banner strip and an avatar circle have the same problem: a cover crop of a picture
 * shaped differently to the frame throws away whatever the reader picked it for. The maths
 * for saying which part to keep is the same for both — it reads the picture's own size
 * against the frame's — so it lives here rather than twice.
 */

import type { Framing } from '../api/client'

/** Below a pixel of room there is nothing to move: the image already fits the frame there. */
export const NO_ROOM = 1

/** A picture may be magnified, never shrunk below the crop that fills the frame. */
export const COVER = 100
export const CLOSEST = 300

export const clamp = (value: number) => Math.min(100, Math.max(0, value))

export const framingOf = (held: Framing): Framing => ({
  focusX: held.focusX,
  focusY: held.focusY,
  zoom: held.zoom,
})

/**
 * How much of the image is out of sight in each axis, in pixels of the frame.
 *
 * <p>What a drag is measured against, so the picture keeps pace with the pointer rather than
 * racing it on a tall image and crawling on a wide one.
 */
export const hidden = (node: HTMLImageElement | null, zoomPercent: number) => {
  if (!node?.naturalWidth || !node.naturalHeight) return null

  // The laid-out size, not the drawn one: getBoundingClientRect would carry the zoom.
  const width = node.offsetWidth
  const height = node.offsetHeight
  const aspect = node.naturalWidth / node.naturalHeight
  const zoom = zoomPercent / 100

  return {
    x: (Math.max(width, height * aspect) - width) * zoom + width * (zoom - 1),
    y: (Math.max(height, width / aspect) - height) * zoom + height * (zoom - 1),
  }
}

/** Where a drag started, and the framing it started from. */
export type DragOrigin = { x: number; y: number; from: Framing }

/** The framing a pointer at this position means, given where the drag took hold. */
export const draggedTo = (
  origin: DragOrigin,
  room: { x: number; y: number },
  clientX: number,
  clientY: number,
): Framing => ({
  ...origin.from,
  focusX:
    room.x < NO_ROOM
      ? origin.from.focusX
      : clamp(origin.from.focusX - ((clientX - origin.x) * 100) / room.x),
  focusY:
    room.y < NO_ROOM
      ? origin.from.focusY
      : clamp(origin.from.focusY - ((clientY - origin.y) * 100) / room.y),
})

/**
 * How the picture is drawn under a framing.
 *
 * <p>The transform is anchored to the same point the crop is, so zooming closes in on what is
 * in view rather than on the middle of a picture the reader has already moved away from.
 */
export const framedStyle = (framing: Framing) => ({
  objectPosition: `${framing.focusX}% ${framing.focusY}%`,
  transform: `scale(${framing.zoom / 100})`,
  transformOrigin: `${framing.focusX}% ${framing.focusY}%`,
})
