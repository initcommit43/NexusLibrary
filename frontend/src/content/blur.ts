import { useEffect, useSyncExternalStore } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/useAuth'

/**
 * Whether the signed-in reader wants 18+ covers drawn behind a blur.
 *
 * <p>Held once for the whole app rather than read per cover: covers appear in a dozen places,
 * and a hook that fetched per cover would spend a request a poster.
 *
 * <p>Keyed by the reader it was read for. The value outlives any one page, and a tab that signs
 * out and in as someone else must not hand the second reader the first one's choice. A value
 * for anyone else reads as blurred, which is also the answer while a read is in flight: a cover
 * that starts sharp and blurs a moment later has already been seen.
 */
type BlurState = { userId: number | null; blurAdult: boolean }

let state: BlurState = { userId: null, blurAdult: true }

/** The reader a read was last started for, so several covers mounting at once ask once. */
let askedFor: number | null = null

const listeners = new Set<() => void>()

const subscribe = (listener: () => void) => {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

/** Publishes a value just saved, so the covers already on screen follow the switch. */
export const publishBlurAdult = (userId: number, blurAdult: boolean) => {
  state = { userId, blurAdult }
  askedFor = userId
  listeners.forEach((listener) => listener())
}

export const useBlurAdult = (): boolean => {
  const userId = useAuth().user?.id ?? null

  useEffect(() => {
    if (userId === null || askedFor === userId) return
    askedFor = userId
    // A failed read leaves the reader on the blurred default, which errs the safe way.
    api
      .contentPreferences()
      .then((preferences) => {
        if (askedFor === userId) publishBlurAdult(userId, preferences.blurAdult)
      })
      .catch(() => undefined)
  }, [userId])

  const current = useSyncExternalStore(subscribe, () => state)
  return current.userId === userId ? current.blurAdult : true
}

/**
 * A cover's class list with the blur added when it applies. A class rather than a wrapper, so
 * no layout that draws a cover has to change shape for it.
 */
export const coverBlurClass = (
  adult: boolean | undefined,
  blur: boolean,
  base?: string,
): string | undefined => [base, adult && blur ? 'nsfw-blur' : null].filter(Boolean).join(' ') || undefined
