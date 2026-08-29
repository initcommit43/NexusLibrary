import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ApiError,
  api,
  type ActivityEntry,
  type MediaType,
  type TrackedItem,
} from '../api/client'
import { ActivityFeed } from '../components/ActivityFeed'
import { AppShell } from '../components/AppShell'
import { PosterGallery, type Poster } from '../components/PosterGallery'
import { ShelfGallery } from '../components/ShelfGallery'
import { moduleOf } from '../components/activity'
import { countdown } from '../components/mediaDetail'
import { episodesWaiting, progressSummary } from '../components/progress'
import { detailPathFor } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

/*
 * Your own shelves show everything they hold rather than a first handful: twenty airing anime
 * is twenty covers, and a shelf that stopped at ten would hide exactly the ones a countdown is
 * for. Only the catalogue shelves cut off, because those lists have no end.
 */

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
  const [feed, setFeed] = useState<ActivityEntry[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.listEntries(), api.activityFeed()])
      .then(([library, activity]) => {
        setEntries(library)
        setFeed(activity)
      })
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

  const withStatus = (mediaType: MediaType, status: TrackedItem['status']) =>
    mine.filter((entry) => entry.mediaType === mediaType && entry.status === status)

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

  const onHold = useMemo(() => mine.filter((entry) => entry.status === 'PAUSED'), [mine])

  const activity = useMemo(
    () =>
      (feed ?? []).filter((entry) => moduleOf(entry)?.slug === module.slug).slice(0, FEED_ROWS),
    [feed, module],
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
          title: `${type.label} in progress`,
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

  const loaded = entries !== null && feed !== null

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

  const catalogue = (
    <>
      {module.types.map((type) => (
        <ShelfGallery
          key={`trending-${type.mediaType}`}
          title={`Trending ${type.label.toLowerCase()}`}
          mediaType={type.mediaType}
          shelf="trending"
          moduleSlug={module.slug}
          typeSlug={type.slug}
        />
      ))}

      {module.types.map((type) => (
        <ShelfGallery
          key={`new-${type.mediaType}`}
          title={`Newly added ${type.label.toLowerCase()}`}
          mediaType={type.mediaType}
          shelf="newly-added"
          moduleSlug={module.slug}
          typeSlug={type.slug}
        />
      ))}
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
          <div className="home-main">
            <section className="status-section">
              <h2>
                Activity
                <Link className="section-action" to="/activity">
                  See all →
                </Link>
              </h2>
              {activity.length > 0 ? (
                <ActivityFeed feed={activity} />
              ) : (
                <p className="muted">
                  Nothing recorded here yet.{' '}
                  <Link to={`/search?module=${module.slug}`}>Track something</Link> and it turns
                  up here.
                </p>
              )}
            </section>
          </div>

          <aside className="home-side">
            {!foldedOut && shelfStack}
            {catalogue}
          </aside>
        </div>
      )}
    </AppShell>
  )
}
