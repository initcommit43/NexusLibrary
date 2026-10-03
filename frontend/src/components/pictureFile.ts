/**
 * Getting a chosen file ready to crop and send.
 *
 * <p>The server is what keeps a hostile file out — it reads, bounds and re-encodes every upload
 * itself and trusts nothing said here. This is for the reader: a wrong file is turned away at
 * once with a reason, rather than after an upload, and a phone photo arrives the right way up.
 * A camera stores its pictures sideways with a note saying which way to turn them; the browser
 * honours that note when it draws, and the server, which reads no notes, would not.
 */

/** The limit on what may be chosen, matching the server's own. */
export const MAX_PICTURE_BYTES = 1024 * 1024

const ACCEPTED = ['image/jpeg', 'image/png']

/** Plenty for a circle drawn at seven rem, and small enough to stay under the limit once drawn. */
const LONGEST_SIDE = 1600

/** Tried in turn until the redrawn picture fits under the limit. */
const QUALITIES = [0.9, 0.8, 0.7]

export class PictureFileError extends Error {}

export const preparePicture = async (file: File): Promise<Blob> => {
  if (!ACCEPTED.includes(file.type)) {
    throw new PictureFileError('Only JPEG and PNG pictures can be uploaded.')
  }
  if (file.size > MAX_PICTURE_BYTES) {
    throw new PictureFileError('That picture is larger than 1 MB. Please choose a smaller one.')
  }

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new PictureFileError('That file could not be read as a picture.')
  }

  try {
    const scale = Math.min(1, LONGEST_SIDE / Math.max(bitmap.width, bitmap.height))
    const canvas = document.createElement('canvas')
    canvas.width = Math.max(1, Math.round(bitmap.width * scale))
    canvas.height = Math.max(1, Math.round(bitmap.height * scale))
    const context = canvas.getContext('2d')
    if (!context) throw new PictureFileError('That file could not be read as a picture.')

    // White under a transparent PNG, as the server would put it: a JPEG has no transparency.
    context.fillStyle = '#ffffff'
    context.fillRect(0, 0, canvas.width, canvas.height)
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)

    for (const quality of QUALITIES) {
      const blob = await new Promise<Blob | null>((done) => canvas.toBlob(done, 'image/jpeg', quality))
      if (blob && blob.size <= MAX_PICTURE_BYTES) return blob
    }
    throw new PictureFileError('That picture is larger than 1 MB. Please choose a smaller one.')
  } finally {
    bitmap.close()
  }
}
