/**
 * Hands a file the app already holds to the browser's downloader.
 *
 * <p>An object URL rather than a link to the endpoint: the export is an authenticated
 * request, and a plain href would be a second one with no token on it.
 */
export const saveFile = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename

  // Firefox only follows a click on an element that is in the document.
  document.body.append(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}
