import { useEffect, useRef, useState } from 'react'
import { ApiError, api, type ActivityEntry } from '../api/client'
import { moduleOf } from './activity'

/**
 * How many events are fetched for each page of rows shown.
 *
 * <p>More than the rows themselves, because the feed on screen is one module's and what comes
 * back is every module's: a reader whose last week was all games would otherwise press for
 * more anime and be handed nothing.
 */
const FETCHED_PER_PAGE = 5

/**
 * As far as one page will reach back on its own.
 *
 * <p>A module with three events of its own among a thousand of another's would otherwise show
 * a button that fetches and fetches and never fills a row: the feed reaches back for it
 * instead, up to the server's own ceiling, and then says plainly that there is no more.
 */
const MOST_FETCHED = 1000

/**
 * The feed of one module, a page at a time.
 *
 * <p>A feed has no natural end — an imported library brings years of it — so it stops at a
 * page and is asked to go on. What has been asked for is not remembered: coming back to the
 * page starts at the top again, which is where someone opening it wants to be.
 */
export const useActivityFeed = (moduleSlug: string, pageRows: number) => {
  const [all, setAll] = useState<ActivityEntry[] | null>(null)
  /*
   * Counted in pages rather than in rows, because how many rows a page holds is decided by
   * the page around it and can change under a reader — a window resized while they read.
   * Holding pages keeps "one more" meaning one more of whatever a page currently is.
   */
  const [pages, setPages] = useState(1)
  const [fetching, setFetching] = useState(pageRows * FETCHED_PER_PAGE)
  /** Set once the server answers with less than was asked for: there is no more behind it. */
  const [complete, setComplete] = useState(false)
  const [error, setError] = useState<string | null>(null)

  /*
   * How many rows are on screen, kept where the fetch can read it without depending on it: a
   * page growing is not a reason to ask the server the same question again.
   */
  const wanted = useRef(pageRows)
  useEffect(() => {
    wanted.current = pages * pageRows
  }, [pages, pageRows])

  useEffect(() => {
    let current = true

    api
      .activityFeed(fetching)
      .then((events) => {
        if (!current) return
        setAll(events)

        const ended = events.length < fetching
        setComplete(ended)

        /*
         * The feed on screen is one module's and what came back is every module's, so a page
         * that has not filled may only mean the answer was full of somebody else's. Reach
         * further back before letting the page settle, so the button under it says something
         * true: a module with three events in it has no more to give.
         */
        const ours = events.filter((event) => moduleOf(event)?.slug === moduleSlug).length
        if (!ended && ours < wanted.current && fetching < MOST_FETCHED) {
          setFetching((held) => Math.min(MOST_FETCHED, held + pageRows * FETCHED_PER_PAGE))
        }
      })
      .catch((err) => {
        if (!current) return
        setError(err instanceof ApiError ? err.message : 'Could not load your activity.')
      })

    return () => {
      current = false
    }
  }, [fetching, moduleSlug, pageRows])

  const shown = pages * pageRows
  const mine = (all ?? []).filter((event) => moduleOf(event)?.slug === moduleSlug)
  const rows = mine.slice(0, shown)


  const more = () => {
    const next = shown + pageRows
    setPages((held) => held + 1)
    // Reach for more from the server only when this module's share of what is held would not
    // fill the next page anyway.
    if (mine.length < next && !complete) {
      setFetching((held) => held + pageRows * FETCHED_PER_PAGE)
    }
  }

  /*
   * Gone from the list as it is pressed, and put back if the server would not have it: the
   * row is the only thing that changes, so waiting on the round trip would make a press feel
   * unheard.
   */
  const forget = async (activityId: string) => {
    const held = all
    setAll((current) => current?.filter((event) => event.id !== activityId) ?? null)
    setError(null)
    try {
      await api.forgetActivity(activityId)
    } catch (err) {
      setAll(held)
      setError(err instanceof ApiError ? err.message : 'Could not delete that.')
    }
  }

  return {
    rows,
    /**
     * Whether there is more of this module's history to show.
     *
     * <p>Only when a page has actually filled: a module with three events in it has no more to
     * give, and a button that cannot change the page is a button that should not be on it.
     */
    hasMore: mine.length > shown,
    more,
    forget,
    loading: all === null,
    error,
  }
}
