import { useEffect, useState } from 'react'
import { api, type ProfilePicture } from '../api/client'
import { useAuth } from '../auth/useAuth'

/**
 * The reader's own profile picture, held once for every place that shows it.
 *
 * <p>The header and the profile both draw it, and a picture changed on the profile has to
 * change in the header at the same moment rather than on the next page load. Held against the
 * user it belongs to, so an account signed into the same tab afterwards never sees the last
 * one's face.
 */
let held: { userId: number; picture: ProfilePicture | null } | null = null
const listeners = new Set<() => void>()

export const publishOwnPicture = (userId: number, picture: ProfilePicture | null) => {
  held = { userId, picture }
  listeners.forEach((listener) => listener())
}

export const useOwnPicture = (): ProfilePicture | null => {
  const { user } = useAuth()
  const [, rerender] = useState(0)

  useEffect(() => {
    const listener = () => rerender((count) => count + 1)
    listeners.add(listener)
    return () => {
      listeners.delete(listener)
    }
  }, [])

  useEffect(() => {
    if (!user || held?.userId === user.id) return
    api
      .profilePicture()
      .then((picture) => publishOwnPicture(user.id, picture))
      // No picture to show is the plain icon, not a broken header.
      .catch(() => {})
  }, [user])

  return user && held?.userId === user.id ? held.picture : null
}

/**
 * An uploaded picture's address, fetched with the reader's token since nothing else may load
 * it. Fetched again only when the upload changes, and let go of when it does.
 */
export const useUploadedSrc = (version: string | null) => {
  const [held, setHeld] = useState<{ version: string; src: string } | null>(null)

  useEffect(() => {
    if (!version) return
    let current = true
    let src: string | null = null
    api
      .profilePictureImage()
      .then((blob) => {
        if (!current) return
        src = URL.createObjectURL(blob)
        setHeld({ version, src })
      })
      // A picture that will not load leaves the plain icon, not a broken page.
      .catch(() => {})
    return () => {
      current = false
      if (src) URL.revokeObjectURL(src)
    }
  }, [version])

  return held?.version === version ? held.src : null
}
