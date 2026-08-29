import { useEffect, useMemo, useState } from 'react'
import { Navigate, NavLink, useParams } from 'react-router-dom'
import { ApiError, api, type MediaType, type TrackedItem, type TrackingStatus } from '../api/client'
import { AppShell } from '../components/AppShell'
import { Bars } from '../components/Bars'
import { Figures, type Figure } from '../components/Figures'
import { Leaderboard } from '../components/charts/Leaderboard'
import { Scatter } from '../components/charts/Scatter'
import { SplitLegend, StackedBar } from '../components/charts/StackedBar'
import { Tooltip } from '../components/charts/Tooltip'
import { useTooltip } from '../components/charts/useTooltip'
import { MODULES, detailPathFor, statusLabelsFor } from '../modules/registry'
import {
  STATUS_ORDER,
  achievementTally,
  countBy,
  countByValue,
  creditKeyFor,
  discriminates,
  genrePoints,
  mean,
  ownScores,
  publicScores,
  releaseYears,
  scoreBuckets,
  scorePairs,
  statusSplit,
  summarise,
  topByProgress,
  type CountRow,
  type GenreField,
  type StatusSlice,
} from '../components/stats'

/*
 * The six media are not equally endowed: anime carries hundreds of ratings and six formats,
 * games have no format field at all, films are all one format and rated by nobody but the
 * crowd. So nothing on this page is a template stamped six times — every section and panel
 * states what its data must support and takes itself off the page when the shelf cannot,
 * which is why the rail is computed rather than written out.
 */

/** Every shelf the app has, and the reading across all of them at once. */
const LENSES: { key: string; label: string; mediaType: MediaType | null }[] = [
  { key: 'all', label: 'All', mediaType: null },
  ...MODULES.flatMap((module) =>
    module.types.map((type) => ({
      key: type.mediaType,
      label: type.label,
      mediaType: type.mediaType,
    })),
  ),
]

/** Words for the shared legend on the all lens, where no one medium's verbs apply. */
const PLAIN_STATUS: Record<TrackingStatus, string> = {
  PLANNING: 'Planned',
  IN_PROGRESS: 'In progress',
  COMPLETED: 'Completed',
  PAUSED: 'On hold',
  DROPPED: 'Dropped',
}

/** Films rank by runtime, not by being watched more than once, and the title must not lie. */
const BOARD_TITLES: Record<MediaType, string> = {
  GAME: 'Most played',
  ANIME: 'Most watched',
  SHOW: 'Most watched',
  MANGA: 'Most read',
  BOOK: 'Most read',
  MOVIE: 'Longest sittings',
}

const withLabels = (slices: StatusSlice[], labels: Record<TrackingStatus, string>) =>
  slices.map((slice) => ({ label: labels[slice.status], amount: slice.amount }))

/** Which verdict a list can honestly rank by, given how many rows actually carry one. */
interface SectionProps {
  entries: TrackedItem[]
  mediaType: MediaType | null
}

/** What a ranked list can be read in order of. */
type Ordering = 'count' | 'own' | 'public'

const ORDERING_LABELS: Record<Ordering, string> = {
  count: 'by count',
  own: 'by your score',
  public: 'by public score',
}

/**
 * A ranked list of whatever a library is made of, with both verdicts on each row.
 *
 * <p>Both, always: the reader's own score where they gave one and the crowd's beside it. An
 * earlier version showed whichever it had more of, which meant a column of numbers whose
 * meaning changed with the shelf you were looking at.
 *
 * <p>One column, however long the list. Split across two, the eye reads along the rows rather
 * than down the columns, and a list sorted top to bottom looks shuffled.
 *
 * <p>Sorting names the column it sorts by for the same reason: "by score" had to guess which
 * score it meant, and guessed differently from the one the row was showing.
 */
const RankedPanel = ({
  title,
  rows,
  width = 'half',
}: {
  title: string
  rows: CountRow[]
  width?: 'half' | 'full'
}) => {
  const [order, setOrder] = useState<Ordering>('count')
  if (!discriminates(rows)) return null

  const scored = (pick: (row: CountRow) => number | null | undefined) =>
    rows.filter((row) => (pick(row) ?? null) !== null).length

  // A column nobody can sort by is not offered: three rated genres is not an order.
  const orderings: Ordering[] = [
    'count',
    ...(scored((row) => row.meanScore) >= 3 ? (['own'] as Ordering[]) : []),
    ...(scored((row) => row.publicScore) >= 3 ? (['public'] as Ordering[]) : []),
  ]

  const key = (row: CountRow) =>
    order === 'count' ? row.amount : order === 'own' ? row.meanScore : (row.publicScore ?? null)

  // Unrated rows sink rather than scatter: they have no place in an order they cannot join.
  const shown =
    order === 'count' ? rows : [...rows].sort((a, b) => (key(b) ?? -1) - (key(a) ?? -1))

  const peak = Math.max(...rows.map((row) => row.amount))

  return (
    <section className={`status-section panel-${width}`}>
      <h2>
        {title}
        {orderings.length > 1 && (
          <span className="metric-toggle section-action">
            {orderings.map((candidate) => (
              <button
                key={candidate}
                type="button"
                className={order === candidate ? 'ghost small on' : 'ghost small'}
                aria-pressed={order === candidate}
                onClick={() => setOrder(candidate)}
              >
                {ORDERING_LABELS[candidate]}
              </button>
            ))}
          </span>
        )}
      </h2>

      {/* Not .ranked-list: the browse shelves own that name, and sharing it once broke
          this layout — the flex definition there beat the grid one here and the bars lost
          their common x. */}
      <ul className="count-list">
        <li className="count-head muted" aria-hidden="true">
          <span className="count-label" />
          <span className="count-track" />
          <span className="count-amount">held</span>
          <span className="count-score">you</span>
          <span className="count-score">public</span>
        </li>
        {shown.map((row) => (
          <li key={row.label}>
            <span className="count-label" title={row.label}>
              {row.label}
            </span>
            <span className="count-track" aria-hidden="true">
              <span
                className="count-fill"
                style={{ width: `${Math.round((row.amount / peak) * 100)}%` }}
              />
            </span>
            <span className="count-amount">{row.amount.toLocaleString()}</span>
            {/* An em dash rather than a nought: unrated is not a rating of zero. */}
            <span className="count-score">{row.meanScore?.toFixed(1) ?? '—'}</span>
            <span className="count-score muted">{row.publicScore?.toFixed(1) ?? '—'}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}

const OverviewSection = ({ entries, mediaType }: SectionProps) => {
  const tip = useTooltip()

  const summary = useMemo(() => summarise(entries, mediaType ?? undefined), [entries, mediaType])
  const crowd = useMemo(() => publicScores(entries), [entries])
  const years = useMemo(() => releaseYears(entries), [entries])
  const formats = useMemo(
    () => (mediaType === null ? [] : countByValue(entries, 'format')),
    [entries, mediaType],
  )
  const board = useMemo(
    () => (mediaType === null ? [] : topByProgress(entries, mediaType)),
    [entries, mediaType],
  )
  const achievements = useMemo(
    () => (mediaType === 'GAME' ? achievementTally(entries) : null),
    [entries, mediaType],
  )

  /*
   * The cross-medium reading no single shelf can give: the same five statuses, one bar per
   * medium, sharing one label column so every proportion starts from the same x.
   */
  const perMedium = useMemo(
    () =>
      mediaType !== null
        ? []
        : MODULES.flatMap((module) => module.types)
            .map((type) => ({
              label: type.label,
              slices: statusSplit(
                entries.filter((entry) => entry.mediaType === type.mediaType),
              ),
            }))
            .filter((row) => row.slices.some((slice) => slice.amount > 0)),
    [entries, mediaType],
  )

  const slices = useMemo(() => statusSplit(entries), [entries])
  const drawnStatuses = slices.filter((slice) => slice.amount > 0).length

  const crowdMean = mean(crowd)
  const statusLabels = mediaType === null ? PLAIN_STATUS : statusLabelsFor(mediaType)

  const legend = STATUS_ORDER.flatMap((status, rank) => {
    const drawn =
      mediaType !== null
        ? slices[rank].amount > 0
        : perMedium.some((row) => row.slices[rank].amount > 0)
    return drawn ? [{ label: statusLabels[status], rank }] : []
  })

  /*
   * A mean of one rating is that rating wearing a statistician's hat, so under five the
   * headline goes to the crowd — labelled as theirs — and the reader's own mean waits
   * until it means something.
   */
  const scoreFigure: Figure[] =
    summary.meanScore !== null && summary.rated >= 5
      ? [
          {
            label: 'mean score',
            value: summary.meanScore.toFixed(1),
            hint: `${summary.rated.toLocaleString()} rated`,
          },
        ]
      : crowdMean !== null
        ? [
            {
              label: 'public score',
              value: crowdMean.toFixed(1),
              hint: `mean of ${crowd.length.toLocaleString()}`,
            },
          ]
        : summary.meanScore !== null
          ? [
              {
                label: 'mean score',
                value: summary.meanScore.toFixed(1),
                hint: `${summary.rated.toLocaleString()} rated`,
              },
            ]
          : []

  const figures: Figure[] = [
    { label: 'entries', value: summary.tracked.toLocaleString() },
    { label: 'completed', value: summary.completed.toLocaleString() },
    ...scoreFigure,
    ...(summary.time
      ? [{ label: summary.time.unit, value: summary.time.amount.toLocaleString() }]
      : []),
    ...(achievements
      ? [
          {
            label: 'achievements',
            value: achievements.unlocked.toLocaleString(),
            hint: `of ${achievements.total.toLocaleString()}`,
          },
        ]
      : []),
  ]

  return (
    <>
      <div className="panel-full">
        <Figures figures={figures} />
      </div>

      {/*
        * Only where the shelf is actually divided. A medium whose every title is completed —
        * films here — draws one solid bar and a legend of one, which is the same one-slice
        * non-answer a format breakdown gives when a medium has only one format. The
        * cross-medium bars stand on their own, since comparing shelves is the reading there.
        */}
      {(mediaType === null || drawnStatuses >= 2) && (
        <section className="status-section panel-full">
          <h2>Where things stand</h2>
          {mediaType !== null ? (
            <StackedBar slices={withLabels(slices, statusLabels)} tip={tip} />
          ) : (
            <div className="split-group">
              {perMedium.map((row) => (
                <StackedBar
                  key={row.label}
                  label={row.label}
                  slices={withLabels(row.slices, PLAIN_STATUS)}
                  tip={tip}
                />
              ))}
            </div>
          )}
          <SplitLegend items={legend} />
          <Tooltip tip={tip.tip} />
        </section>
      )}

      {/* Half beside the formats where a medium has them; the whole row where it does not. */}
      {mediaType !== null && board.length >= 3 && (
        <section
          className={`status-section panel-${discriminates(formats) ? 'half' : 'full'}`}
        >
          <h2>{BOARD_TITLES[mediaType]}</h2>
          <Leaderboard
            rows={board.map(({ entry, amount, figure }) => ({
              id: entry.id,
              title: entry.title,
              coverUrl: entry.coverUrl,
              to: detailPathFor(entry),
              amount,
              figure,
            }))}
          />
        </section>
      )}

      <RankedPanel title="Formats" rows={formats} />

      {years.length > 0 && (
        <div className="panel-full">
          <Bars rows={years} title="When it's from" />
        </div>
      )}
    </>
  )
}

const ScoresSection = ({ entries }: SectionProps) => {
  const crowd = useMemo(() => publicScores(entries), [entries])
  const own = useMemo(() => ownScores(entries), [entries])
  const pairs = useMemo(() => scorePairs(entries), [entries])

  const crowdRows = useMemo(() => scoreBuckets(crowd), [crowd])
  const ownRows = useMemo(() => scoreBuckets(own), [own])

  // Both axes share one span so the line of agreement runs corner to corner at 45 degrees.
  const lowest =
    pairs.length > 0 ? Math.min(...pairs.map((pair) => Math.min(pair.own, pair.crowd))) : 0
  const domain: [number, number] = [Math.max(0, Math.floor(lowest) - 1), 10]

  const ownShown = own.length >= 5

  return (
    <>
      {/* The two spreads pair up when both exist — same buckets, same width, one glance. */}
      {discriminates(crowdRows) && (
        <div className={ownShown ? 'panel-half' : 'panel-full'}>
          <Bars rows={crowdRows} title="How the crowd scores" />
        </div>
      )}
      {ownShown && (
        <div className="panel-half">
          <Bars rows={ownRows} title="How you score" />
        </div>
      )}
      {pairs.length >= 5 && (
        <section className="status-section panel-full">
          <h2>You against the crowd</h2>
          <p className="panel-note muted">
            One dot per title you rated. Above the line you liked it more than most did — the
            far dots are the arguments.
          </p>
          <Scatter
            points={pairs.map((pair) => ({
              x: pair.crowd,
              y: pair.own,
              label: pair.entry.title,
              lines: [`you ${pair.own.toFixed(1)} · crowd ${pair.crowd.toFixed(1)}`],
            }))}
            xDomain={domain}
            yDomain={domain}
            xLabel="the crowd's score"
            yLabel="your score"
            agreement
          />
        </section>
      )}
    </>
  )
}

const GenreFieldPanel = ({ field }: { field: GenreField }) => {
  const most = Math.max(...field.points.map((point) => point.amount))
  const low = Math.min(...field.points.map((point) => point.score))
  const high = Math.max(...field.points.map((point) => point.score))

  return (
    <section className="status-section panel-full">
      <h2>Where genres fall</h2>
      <p className="panel-note muted">
        {field.basis === 'own'
          ? 'How much of a genre you hold, against how you score it. The dashed medians cut the field into quarters.'
          : "How much of a genre you hold, against how the crowd scores it — you haven't rated enough here to speak for yourself."}
      </p>
      <Scatter
        points={field.points.map((point) => ({
          x: point.amount,
          y: point.score,
          weight: point.amount,
          label: point.label,
          lines: [`${point.amount.toLocaleString()} titles · scored ${point.score.toFixed(1)}`],
        }))}
        xDomain={[0, Math.ceil(most * 1.08)]}
        yDomain={[Math.max(0, Math.floor(low - 0.5)), Math.min(10, Math.ceil(high + 0.5))]}
        xLabel="titles held"
        yLabel={field.basis === 'own' ? 'your score' : 'public score'}
        quadrants={{
          x: field.medianCount,
          y: field.medianScore,
          corners: ['rare but loved', 'the sweet spot', 'habit', 'neither'],
        }}
      />
    </section>
  )
}

const GenresSection = ({ entries }: SectionProps) => {
  const field = useMemo(() => genrePoints(entries), [entries])
  const rows = useMemo(() => countBy(entries, 'genres', 15), [entries])

  return (
    <>
      {field !== null && field.points.length >= 5 && <GenreFieldPanel field={field} />}
      <RankedPanel title="Top genres" rows={rows} width="full" />
    </>
  )
}

const CreditsSection = ({ entries, mediaType }: SectionProps) => {
  const credit = mediaType === null ? null : creditKeyFor(mediaType)
  const rows = useMemo(
    () => (credit === null ? [] : countBy(entries, credit.key)),
    [entries, credit],
  )
  if (credit === null) return null
  return <RankedPanel title={credit.title} rows={rows} width="full" />
}

export const StatsPage = () => {
  const { lens: lensParam } = useParams()
  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [reading, setReading] = useState('overview')

  useEffect(() => {
    api
      .listEntries()
      .then(setEntries)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your library.'),
      )
  }, [])

  const chosen = LENSES.find((candidate) => candidate.key === lensParam)

  const shown = useMemo(() => {
    const all = entries ?? []
    return !chosen || chosen.mediaType === null
      ? all
      : all.filter((entry) => entry.mediaType === chosen.mediaType)
  }, [entries, chosen])

  /*
   * The rail is read off the data, never written out: a section that cannot fill itself —
   * one genre, one platform, a medium with no studios field at all — is not drawn and gets
   * no link either.
   */
  const sections = useMemo(() => {
    if (entries === null || !chosen || shown.length === 0) return []

    // heading: false where the section holds one panel that already carries the name —
    // the rail still needs the label, but the page should not say Studios twice.
    const list: { key: string; label: string; heading?: boolean }[] = [
      { key: 'overview', label: 'Overview' },
    ]
    if (discriminates(scoreBuckets(publicScores(shown))) || ownScores(shown).length >= 5) {
      list.push({ key: 'scores', label: 'Scores' })
    }
    if (discriminates(countBy(shown, 'genres', 2))) {
      list.push({ key: 'genres', label: 'Genres' })
    }
    const credit = chosen.mediaType === null ? null : creditKeyFor(chosen.mediaType)
    if (credit !== null && discriminates(countBy(shown, credit.key, 2))) {
      list.push({ key: 'credits', label: credit.title, heading: false })
    }
    return list
  }, [entries, chosen, shown])

  const ids = sections.map((section) => section.key).join(',')

  /*
   * The rail marks where the page is, not the last thing clicked — the same band just under
   * the header that the settings rail reads, so both pages mark themselves the same way.
   */
  useEffect(() => {
    if (!ids) return

    const observer = new IntersectionObserver(
      (crossings) => {
        const crossing = crossings.find((entry) => entry.isIntersecting)
        if (crossing) setReading(crossing.target.id)
      },
      { rootMargin: '-72px 0px -70% 0px' },
    )

    for (const id of ids.split(',')) {
      const node = document.getElementById(id)
      if (node) observer.observe(node)
    }

    return () => observer.disconnect()
  }, [ids])

  // Anything the URL gets wrong resolves to the nearest page that exists, not an error.
  if (!chosen) return <Navigate to="/stats/all" replace />

  const loaded = entries !== null

  const bodyFor = (key: string) => {
    switch (key) {
      case 'overview':
        return <OverviewSection entries={shown} mediaType={chosen.mediaType} />
      case 'scores':
        return <ScoresSection entries={shown} mediaType={chosen.mediaType} />
      case 'genres':
        return <GenresSection entries={shown} mediaType={chosen.mediaType} />
      default:
        return <CreditsSection entries={shown} mediaType={chosen.mediaType} />
    }
  }

  return (
    <AppShell>
      <h1>Stats</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {!loaded && !error && <p className="muted">Reading your library…</p>}

      {loaded && (
        <>
          {/* One shelf at a time, or the whole library added together. */}
          <nav className="lens-bar" aria-label="Shelf">
            {LENSES.map((candidate) => (
              <NavLink
                key={candidate.key}
                to={`/stats/${candidate.key}`}
                className={({ isActive }) => (isActive ? 'lens-link on' : 'lens-link')}
              >
                {candidate.label}
              </NavLink>
            ))}
          </nav>

          {shown.length === 0 ? (
            <p className="muted">
              Nothing on this shelf yet. Track something and its figures appear here.
            </p>
          ) : (
            <div className="stats-layout">
              <nav className="stats-rail" aria-label="Stats sections">
                {sections.map((section) => (
                  <a
                    key={section.key}
                    href={`#${section.key}`}
                    className={reading === section.key ? 'active' : undefined}
                    aria-current={reading === section.key ? 'true' : undefined}
                  >
                    {section.label}
                  </a>
                ))}
              </nav>

              {/* Every section on one page: a rail that hides three quarters of the figures
                  behind clicks makes a reader remember what they came for. */}
              <div className="stats-main">
                {sections.map((section) => (
                  <section key={section.key} id={section.key} className="stats-anchor">
                    {section.heading !== false && (
                      <h2 className="stats-heading">{section.label}</h2>
                    )}
                    <div className="stats-grid">{bodyFor(section.key)}</div>
                  </section>
                ))}
              </div>
            </div>
          )}
        </>
      )}
    </AppShell>
  )
}
