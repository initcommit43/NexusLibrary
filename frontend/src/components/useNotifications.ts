import { useEffect, useState } from 'react'
import { api, type MediaType, type Waiting } from '../api/client'

/** What a reader with nothing waiting has, and what is shown while the answer is on its way. */
const NOTHING: Waiting = { items: [], unread: 0 }

/**
 * What is waiting for a reader in one module, and the two ways of clearing it.
 *
 * <p>Shared by the panel on the home page and by the page behind it, so the two cannot
 * disagree about what "read" does: every call answers with the whole panel, and the count
 * beside the heading is whatever came back rather than something worked out from what was
 * there before.
 */
export const useNotifications = (
  mediaTypes: MediaType[],
  limit: number,
  /**
   * How often to look again, in milliseconds. Zero for pages that should not.
   *
   * <p>Only the home page asks for this. An episode airing is noticed the second it lands
   * now, but a page already open would not hear about it until it was loaded again — and the
   * home page is the one people leave open. Everywhere else, a list quietly rearranging
   * itself under someone who is reading a shelf or moving things around their profile is a
   * page that will not sit still.
   */
  refreshEvery = 0,
) => {
  /*
   * The answer is held with the scope it answers about, so switching module shows nothing
   * rather than the last module's rows: which module a row belongs to is not visible in it,
   * and anime notifications under a games heading read as the app being wrong.
   */
  const [answer, setAnswer] = useState<{ scope: string; waiting: Waiting } | null>(null)
  /** Bumped by the clock below; the only thing that makes the fetch run again unasked. */
  const [beat, setBeat] = useState(0)

  // Joined rather than held as the array: a fresh array every render is a new dependency
  // every render, and the panel would fetch forever.
  const scope = mediaTypes.join(',')
  const asked = () => (scope === '' ? [] : (scope.split(',') as MediaType[]))

  useEffect(() => {
    let current = true

    api
      .notifications(limit, scope === '' ? [] : (scope.split(',') as MediaType[]))
      // Nothing waiting is the answer for anyone who has just arrived, and a panel that
      // cannot load is not worth an alarm on a page about your own library.
      .then((waiting) => current && setAnswer({ scope, waiting }))
      .catch(() => current && setAnswer({ scope, waiting: NOTHING }))

    return () => {
      current = false
    }
  }, [scope, limit, beat])

  /*
   * The clock that asks again. Kept apart from the fetch so that a slow answer cannot stack
   * two of them up: the beat only advances between runs of its own interval.
   */
  useEffect(() => {
    if (refreshEvery <= 0) return

    const timer = window.setInterval(() => setBeat((held) => held + 1), refreshEvery)
    return () => window.clearInterval(timer)
  }, [refreshEvery])

  // A later look is not a load: the rows on screen stay put until the new ones arrive.
  const loading = answer === null || answer.scope !== scope
  const waiting = loading || answer === null ? NOTHING : answer.waiting

  /** Optimistic, then corrected by what the server says; put back as it was if it refuses. */
  const settle = async (shown: Waiting, ask: () => Promise<Waiting>) => {
    const held = answer
    setAnswer({ scope, waiting: shown })
    try {
      setAnswer({ scope, waiting: await ask() })
    } catch {
      setAnswer(held)
    }
  }

  const read = (id: number) =>
    settle(
      {
        items: waiting.items.map((item) => (item.id === id ? { ...item, read: true } : item)),
        unread: Math.max(0, waiting.unread - 1),
      },
      () => api.readNotification(id, limit, asked()),
    )

  const readAll = () =>
    settle({ items: waiting.items.map((item) => ({ ...item, read: true })), unread: 0 }, () =>
      api.readAllNotifications(limit, asked()),
    )

  return { waiting, loading, read, readAll }
}
