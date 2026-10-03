/**
 * Holding a picture inside a frame that is not its shape.
 *
 * <p>A banner strip and an avatar circle have the same problem: a cover crop of a picture
 * shaped differently to the frame throws away whatever the reader picked it for. What is
 * stored is a focus point and a zoom, which is what the frame is drawn with; what the reader
 * handles is a crop drawn over the whole picture. The two are the same thing said two ways,
 * and the conversion lives here rather than in either frame.
 */

import type { Framing } from '../api/client'

/** A picture may be magnified, never shrunk below the crop that fills the frame. */
export const COVER = 100
export const CLOSEST = 300

export const clamp = (value: number, low = 0, high = 100) => Math.min(high, Math.max(low, value))

export const framingOf = (held: Framing): Framing => ({
  focusX: held.focusX,
  focusY: held.focusY,
  zoom: held.zoom,
})

/** A crop as fractions of the picture: its top-left corner, and its size. */
export type Crop = { x: number; y: number; width: number; height: number }

/**
 * How much of the picture a cover crop shows in each axis. Shapes are width over height: a
 * picture wider in proportion than its frame shows all its height and part of its width.
 */
const coverShare = (picture: number, frame: number) => ({
  width: Math.min(1, frame / picture),
  height: Math.min(1, picture / frame),
})

/**
 * The part of the picture a framing shows, given the picture's shape and the frame's.
 *
 * <p>{@link framedStyle} draws a cover crop placed by the focus point and then scales it about
 * that same point, which works out to a window 1/zoom of the cover's size whose corner sits
 * the focus's share of the way along the room left either side of it.
 */
export const cropOf = (framing: Framing, picture: number, frame: number): Crop => {
  const cover = coverShare(picture, frame)
  const zoom = framing.zoom / 100
  const width = cover.width / zoom
  const height = cover.height / zoom
  return {
    x: (framing.focusX / 100) * (1 - width),
    y: (framing.focusY / 100) * (1 - height),
    width,
    height,
  }
}

/** The framing that draws exactly this crop: {@link cropOf}, run backwards. */
export const framingFrom = (crop: Crop, picture: number, frame: number): Framing => {
  const cover = coverShare(picture, frame)
  // Where the crop already spans the picture there is no room to place it in; any focus does.
  const along = (start: number, size: number) => (size > 0.999 ? 50 : clamp((start / (1 - size)) * 100))
  return {
    focusX: Math.round(along(crop.x, crop.width)),
    focusY: Math.round(along(crop.y, crop.height)),
    zoom: Math.round(clamp((cover.width / crop.width) * 100, COVER, CLOSEST)),
  }
}

/** The crop's size limits in picture widths: from the full cover down to the closest zoom. */
export const cropWidths = (picture: number, frame: number) => {
  const cover = coverShare(picture, frame)
  return { widest: cover.width, narrowest: cover.width / (CLOSEST / 100) }
}

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
