import { useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ApiError,
  api,
  type BrowseShelf,
  type MediaType,
  type TrackedItem,
  type Waiting,
} from '../api/client'
import { ActivityFeed } from '../components/ActivityFeed'
import { FeedPicker, type FeedKind } from '../components/FeedPicker'
import { NotificationList } from '../components/NotificationList'
import { AppShell } from '../components/AppShell'
import { PosterGallery, type Poster } from '../components/PosterGallery'
import { ShelfGallery } from '../components/ShelfGallery'
import { countdown } from '../components/mediaDetail'
import { episodesWaiting, progressSummary } from '../components/progress'
import { useActivityFeed } from '../components/useActivityFeed'
import {
  detailPathFor,
  statusLabelsFor,
  type MediaTypeDefinition,
} from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

/*
 * Your own shelves show everything they hold rather than a first handful: twenty airing anime
 * is twenty covers, and a shelf that stopped at ten would hide exactly the ones a countdown is
 * for. Only the catalogue shelves cut off, because those lists have no end.
 */

/**
 * What the shelf of things you are partway through is called, in the module's own words:
 * playing a game, watching a film, reading a book. The medium is named too where a module
 * holds more than one of them, since "Watching" twice over says nothing about which is which.
 */
const inProgressTitle = (type: MediaTypeDefinition, types: number) => {
  const verb = statusLabelsFor(type.mediaType).IN_PROGRESS
  return types > 1 ? `${verb} ${type.listLabel.toLowerCase()}` : verb
}

/** The fewest rows the feed is cut to, however short the column beside it happens to be. */
const MIN_FEED_ROWS = 6

/** A home feed is the last while, not the whole history; the activity page holds the rest. */
const FEED_ROWS = 12

/** Once the airing time has passed, the copy has simply not caught up yet. */
const OUT_NOW = 'out now'

/** Where a reader's choice of arrangement is remembered, per module. */
const FOLDED_KEY = 'nexus.home.folded'

/**
 * Kept in the browser rather than on the account: this is a preference about one screen on one
 * machine, and a wrong answer costs a click rather than data. Guarded because a private window
 * can refuse storage outright.
 */
const readFolded = (moduleSlug: string): boolean => {
  try {
    return window.localStorage.getItem(`${FOLDED_KEY}.${moduleSlug}`) === 'true'
  } catch {
    return false
  }
}

const writeFolded = (moduleSlug: string, out: boolean) => {
  try {
    window.localStorage.setItem(`${FOLDED_KEY}.${moduleSlug}`, String(out))
  } catch {
    // A reader with storage switched off still gets the fold; it just does not outlive the
    // visit, which is a smaller loss than an error over where a shelf sits.
  }
}

/** One of your own shelves, wherever it ends up sitting. */
interface Shelf {
  key: string
  title: string
  posters: Poster[]
  /** Where the whole of it lives, when the shelf here is only part of one. */
  to?: string
  count?: number
}

/** Arrows to the corners, or back from them: the same gesture a video player's corner has. */
const FoldIcon = ({ out }: { out: boolean }) => (
  <svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" aria-hidden>
    {out ? (
      <path
        d="M9 4H4v5M15 4h5v5M9 20H4v-5M15 20h5v-5"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    ) : (
      <path
        d="M4 9V4h5M20 9V4h-5M4 15v5h5M20 15v5h-5"
        strokeWidth="1.8"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    )}
  </svg>
)

const posterOf = (entry: TrackedItem, ...caption: (string | null)[]): Poster => ({
  key: String(entry.id),
  title: entry.title,
  coverUrl: entry.coverUrl,
  to: detailPathFor(entry),
  caption: caption.flatMap((line) => (line ? [line] : [])),
  // The same corner mark the library shelves carry: a countdown says when the next one lands,
  // and this says how many landed while you were not looking.
  waiting: episodesWaiting(entry),
})

/**
 * Home, scoped to the module you are in.
 *
 * <p>Both halves follow the switcher in the header rather than a control of their own: set it
 * to anime and home stays anime, whatever you watched last night.
 *
 * <p>Your own shelves sit beside the feed, or fold out across the page with the feed beneath
 * them — one choice covering all of them, since folding them out one at a time only produces
 * a page half in each arrangement. The catalogue shelves stay in the column either way: they
 * are a taste of a list rather than a part of your library.
 */
export const HomePage = () => {
  const module = useCurrentModule()

  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  /*
   * The feed ends where the column beside it ends.
   *
   * <p>A page of it is however many rows fit alongside the shelves — measured rather than
   * guessed at, because the shelves are a different height for every reader and a fixed count
   * either stops halfway up the page or runs a long way past the bottom of it. "Load more"
   * then adds another column's worth.
   */
  const side = useRef<HTMLDivElement>(null)
  const main = useRef<HTMLDivElement>(null)
  const [feedRows, setFeedRows] = useState(FEED_ROWS)
  const feed = useActivityFeed(module.slug, feedRows)

  /*
   * Which of the two lists the section is showing. Not remembered between visits: the page is
   * opened to see what has happened, and what has happened is usually the feed.
   */
  const [showing, setShowing] = useState<FeedKind>('activity')
  const [waiting, setWaiting] = useState<Waiting>({ items: [], unread: 0 })

  useEffect(() => {
    let current = true

    api
      .notifications()
      // Nothing waiting is the answer for anyone who has just arrived, and a panel that
      // cannot load is not worth an alarm on a page about your own library.
      .then((answer) => current && setWaiting(answer))
      .catch(() => {})

    return () => {
      current = false
    }
  }, [])

  const readAll = async () => {
    const held = waiting
    setWaiting({ items: waiting.items.map((item) => ({ ...item, read: true })), unread: 0 })
    try {
      setWaiting(await api.readAllNotifications())
    } catch {
      setWaiting(held)
    }
  }

  useEffect(() => {
    const column = side.current
    if (!column) return

    /*
     * Measured against where the two columns actually end rather than by adding up the page's
     * parts: the feed grows by however many rows fit in the space left under it, and shrinks
     * when it overruns. Repeats until the two ends are within a row of each other, which is
     * one pass in practice and needs to know nothing about the headings or the button.
     */
    const measure = () => {
      const list = main.current?.querySelector('.activity-feed')
      const row = list?.firstElementChild
      if (!list || !row) return

      const gap = parseFloat(getComputedStyle(list).rowGap) || 0
      const step = row.getBoundingClientRect().height + gap
      const button = main.current?.querySelector('.feed-more')?.getBoundingClientRect().height ?? 0

      const slack = column.getBoundingClientRect().bottom - list.getBoundingClientRect().bottom - button
      if (Math.abs(slack) < step) return

      setFeedRows((held) => Math.max(MIN_FEED_ROWS, held + Math.trunc(slack / step)))
    }

    measure()
    const watcher = new ResizeObserver(measure)
    watcher.observe(column)
    if (main.current) watcher.observe(main.current)
    return () => watcher.disconnect()
    // Re-measured as rows arrive; the observer keeps up with the column beside them.
  }, [module.slug, feed.loading, feed.rows.length])

  /*
   * Which rows this module leads with, asked once per medium. Cheap and cached server-side:
   * the shelf list is a constant the adapter states, not a query against the source.
   */
  const [featured, setFeatured] = useState<Record<string, BrowseShelf[]>>({})

  useEffect(() => {
    let current = true

    Promise.all(
      module.types.map((type) =>
        api
          .browseShelves(type.mediaType)
          .then((shelves) => [type.mediaType, shelves.filter((shelf) => shelf.onHome)] as const)
          // A module whose shelves will not load simply leads with none of them.
          .catch(() => [type.mediaType, [] as BrowseShelf[]] as const),
      ),
    ).then((answers) => current && setFeatured(Object.fromEntries(answers)))

    return () => {
      current = false
    }
  }, [module])

  useEffect(() => {
    api
      .listEntries()
      .then(setEntries)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your library.'),
      )
  }, [])

  /*
   * Keyed by the module it was read for, so switching modules reads that module's own answer
   * during the render rather than in an effect that would set state and render again.
   */
  const [choice, setChoice] = useState(() => ({
    slug: module.slug,
    out: readFolded(module.slug),
  }))
  const foldedOut = choice.slug === module.slug ? choice.out : readFolded(module.slug)

  const fold = () => {
    setChoice({ slug: module.slug, out: !foldedOut })
    writeFolded(module.slug, !foldedOut)
  }

  const mine = useMemo(
    () =>
      (entries ?? []).filter((entry) =>
        module.types.some((type) => type.mediaType === entry.mediaType),
      ),
    [entries, module],
  )

  /**
   * When the reader was last at this title, and nothing else.
   *
   * <p>What happened to the title: an episode logged on the service it came from, a status
   * moved here, the day it was added. Never the entry's own timestamp — an import writes
   * hundreds of rows in one second, and a title it merely rewrote would otherwise sort above
   * one the reader actually watched last week.
   *
   * <p>A title nothing has ever happened to comes last, which is what it is: an import put it
   * on the shelf and it has not been opened since.
   */
  const lastTouched = (entry: TrackedItem) =>
    entry.lastActivityAt === null ? 0 : Date.parse(entry.lastActivityAt)

  /**
   * Last touched first.
   *
   * <p>What someone is partway through is a queue they are working down, and the one they
   * were at a moment ago is the one they are on.
   */
  const byLastTouched = (a: TrackedItem, b: TrackedItem) => {
    const moved = lastTouched(b) - lastTouched(a)
    // A tie is two rows written in the same instant — an import, in practice. Newest entry
    // first there too, so the order is at least the same on every load.
    return moved !== 0 ? moved : b.id - a.id
  }

  const withStatus = (mediaType: MediaType, status: TrackedItem['status']) =>
    mine
      .filter((entry) => entry.mediaType === mediaType && entry.status === status)
      .sort(byLastTouched)

  /*
   * What airs next, soonest first, and only from what you are watching — a countdown for
   * something you have not started is a schedule, and schedules belong on the browse pages.
   * The time is absolute, so a copy cached yesterday still counts down correctly; once it
   * passes, the episode is out and the copy has not caught up, which is what it says.
   */
  const airing = useMemo(() => {
    const next = (entry: TrackedItem) => {
      const value = entry.metadata.nextEpisode
      if (typeof value !== 'object' || value === null) return null

      const { episode, airingAt } = value as Record<string, unknown>
      if (typeof episode !== 'number' || typeof airingAt !== 'number') return null
      return { entry, episode, airingAt }
    }

    return mine
      .filter((entry) => entry.status === 'IN_PROGRESS')
      .map(next)
      .flatMap((found) => (found === null ? [] : [found]))
      .sort((a, b) => a.airingAt - b.airingAt)
      .map(({ entry, episode, airingAt }) =>
        posterOf(entry, `Ep ${episode}`, countdown(airingAt) ?? OUT_NOW),
      )
  }, [mine])

  // Anything counting down above is not repeated below it: the countdown is simply the more
  // useful way to show that half of what you are watching.
  const counting = useMemo(() => new Set(airing.map((poster) => poster.key)), [airing])

  const onHold = useMemo(
    () => mine.filter((entry) => entry.status === 'PAUSED').sort(byLastTouched),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [mine],
  )

  /**
   * Where a shelf's count leads. A section holding one medium belongs to that medium's shelf
   * in the library; one spanning two — anything paused across anime and manga — has no single
   * page to point at, so it says how much it holds and stops there.
   */
  const shelfFor = (held: TrackedItem[]): string | undefined => {
    const media = new Set(held.map((entry) => entry.mediaType))
    if (media.size !== 1) return undefined

    const type = module.types.find((candidate) => media.has(candidate.mediaType))
    return type && `/library/${module.slug}/${type.slug}`
  }

  const airingEntries = mine.filter(
    (entry) => entry.status === 'IN_PROGRESS' && counting.has(String(entry.id)),
  )

  const shelves: Shelf[] = [
    ...(airing.length > 0
      ? [
          {
            key: 'airing',
            title: 'Airing',
            posters: airing,
            to: shelfFor(airingEntries),
            count: airing.length,
          },
        ]
      : []),
    ...module.types.flatMap((type) => {
      const reading = withStatus(type.mediaType, 'IN_PROGRESS').filter(
        (entry) => !counting.has(String(entry.id)),
      )
      if (reading.length === 0) return []

      return [
        {
          key: `progress-${type.mediaType}`,
          title: inProgressTitle(type, module.types.length),
          posters: reading.map((entry) => posterOf(entry, progressSummary(entry))),
          to: `/library/${module.slug}/${type.slug}`,
          count: reading.length,
        },
      ]
    }),
    ...(onHold.length > 0
      ? [
          {
            key: 'on-hold',
            title: 'On hold',
            posters: onHold.map((entry) => posterOf(entry, progressSummary(entry))),
            to: shelfFor(onHold),
            count: onHold.length,
          },
        ]
      : []),
  ]

  const loaded = entries !== null && !feed.loading

  const drawShelf = (shelf: Shelf) => (
    <section key={shelf.key} className="status-section">
      <h2>
        {shelf.title}
        {shelf.count !== undefined &&
          (shelf.to ? (
            // The same blue as "See all", and the same arrow: it reads as a way through
            // because it looks like every other way through on the page.
            <Link className="section-action" to={shelf.to}>
              {shelf.count} →
            </Link>
          ) : (
            <span className="section-action muted">{shelf.count}</span>
          ))}
      </h2>
      <PosterGallery posters={shelf.posters} />
    </section>
  )

  /*
   * The control rides the shelves themselves rather than the page's title: it changes where
   * they sit, so it belongs where they are. Out of sight until the shelves are reached for —
   * a button that rearranges the page is not something to trip over on the way past.
   */
  const foldControl = (
    <button
      type="button"
      className="fold-button"
      aria-pressed={foldedOut}
      aria-label={foldedOut ? 'Fold the shelves back into the column' : 'Fold the shelves out'}
      title={foldedOut ? 'Fold back in' : 'Fold out'}
      onClick={fold}
    >
      <FoldIcon out={!foldedOut} />
    </button>
  )

  const shelfStack = (
    <div className="shelf-stack">
      {foldControl}
      {shelves.map(drawShelf)}
    </div>
  )

  /*
   * The rows a module leads with are the module's own answer, not two shelf ids named here.
   * Asking for "trending" and "newly-added" everywhere meant every module that files its
   * shelves under other names — games under popular and coming soon, films under this week's
   * list — had a home page of empty headings.
   */
  const catalogue = (
    <>
      {module.types.flatMap((type) =>
        (featured[type.mediaType] ?? []).map((shelf) => (
          <ShelfGallery
            key={`${shelf.id}-${type.mediaType}`}
            // The module named the row; the medium is added only where the module holds more
            // than one, since "Popular now" twice over says nothing about which is which.
            title={module.types.length > 1 ? `${shelf.label} · ${type.label}` : shelf.label}
            mediaType={type.mediaType}
            shelf={shelf.id}
            moduleSlug={module.slug}
            typeSlug={type.slug}
          />
        )),
      )}
    </>
  )

  return (
    <AppShell>
      <h1>{module.label}</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {!loaded && !error && <p className="muted">Reading your library…</p>}

      {/* Folded out, the shelves run the width of the page and the feed reads beneath them. */}
      {loaded && foldedOut && shelfStack}

      {loaded && (
        <div className="home-layout">
          <div className="home-main" ref={main}>
            <section className="status-section">
              <h2>
                {/* Two lists in one place, because they answer the same question from either
                    side: what has happened lately — by me, and to what I keep. */}
                <FeedPicker current={showing} unread={waiting.unread} onChoose={setShowing} />

                {showing === 'activity' ? (
                  <Link className="section-action" to="/activity">
                    See all →
                  </Link>
                ) : (
                  waiting.unread > 0 && (
                    <button
                      type="button"
                      className="section-action ghost small"
                      onClick={() => void readAll()}
                    >
                      Read all
                    </button>
                  )
                )}
              </h2>

              {showing === 'notifications' ? (
                waiting.items.length > 0 ? (
                  <NotificationList notifications={waiting.items} />
                ) : (
                  <p className="muted">
                    Nothing yet. An episode airing, or a season appearing, turns up here.
                  </p>
                )
              ) : feed.rows.length > 0 ? (
                <>
                  <ActivityFeed feed={feed.rows} onForget={(id) => void feed.forget(id)} />
                  {/* The feed stops with the page rather than running past everything beside
                      it; a history goes back years, and this is the last while of it. */}
                  {feed.hasMore && (
                    <button type="button" className="ghost feed-more" onClick={feed.more}>
                      Load more
                    </button>
                  )}
                </>
              ) : (
                <p className="muted">
                  Nothing recorded here yet.{' '}
                  <Link to={`/search?module=${module.slug}`}>Track something</Link> and it turns
                  up here.
                </p>
              )}
            </section>
          </div>

          <aside className="home-side" ref={side}>
            {!foldedOut && shelfStack}
            {catalogue}
          </aside>
        </div>
      )}
    </AppShell>
  )
}
