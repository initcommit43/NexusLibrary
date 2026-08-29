import { useEffect, useMemo, useState } from 'react'
import { ApiError, api, type MediaType, type TrackedItem } from '../api/client'
import { AppShell } from '../components/AppShell'
import { Bars } from '../components/Bars'
import { Figures } from '../components/Figures'
import { MODULES } from '../modules/registry'
import {
  countBy,
  countByValue,
  creditKeyFor,
  releaseYears,
  scoreBuckets,
  summarise,
  type CountRow,
} from '../components/stats'

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

/** Past this many rows a single column is a long read where two are a glance. */
const SPLIT_ABOVE = 6

/**
 * A count with the mean score of what it counts.
 *
 * <p>The count says what someone watches and the score beside it says what they liked, which
 * is the pair worth reading — either alone is half a sentence.
 *
 * <p>Bars are drawn against the largest row in the whole list, including when the list is
 * split across two columns: scaling each column to its own longest row would make the second
 * column's smaller counts look like the first column's larger ones.
 */
const Ranked = ({
  title,
  rows,
  split = false,
  asShare = false,
}: {
  title: string
  rows: CountRow[]
  split?: boolean
  asShare?: boolean
}) => {
  if (rows.length === 0) return null

  const peak = Math.max(...rows.map((row) => row.amount))
  const total = rows.reduce((sum, row) => sum + row.amount, 0)

  const half = Math.ceil(rows.length / 2)
  const columns =
    split && rows.length > SPLIT_ABOVE ? [rows.slice(0, half), rows.slice(half)] : [rows]

  return (
    <section className="status-section">
      <h2>{title}</h2>
      <div className={columns.length > 1 ? 'stats-pair' : undefined}>
        {columns.map((column, index) => (
          <ul key={column[0]?.label ?? index} className="ranked-list">
            {column.map((row) => (
              <li key={row.label}>
                <span className="ranked-label" title={row.label}>
                  {row.label}
                </span>
                <span className="ranked-track" aria-hidden="true">
                  <span
                    className="ranked-fill"
                    style={{ width: `${Math.round((row.amount / peak) * 100)}%` }}
                  />
                </span>
                <span className="ranked-amount">
                  {asShare
                    ? `${Math.round((row.amount / total) * 100)}%`
                    : row.amount.toLocaleString()}
                </span>
                <span className="ranked-score muted">
                  {asShare || row.meanScore === null ? '' : row.meanScore.toFixed(1)}
                </span>
              </li>
            ))}
          </ul>
        ))}
      </div>
    </section>
  )
}

export const StatsPage = () => {
  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [lens, setLens] = useState<string>('all')

  useEffect(() => {
    api
      .listEntries()
      .then(setEntries)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your library.'),
      )
  }, [])

  const chosen = LENSES.find((candidate) => candidate.key === lens) ?? LENSES[0]

  const shown = useMemo(() => {
    const all = entries ?? []
    return chosen.mediaType === null
      ? all
      : all.filter((entry) => entry.mediaType === chosen.mediaType)
  }, [entries, chosen.mediaType])

  const summary = useMemo(
    () => summarise(shown, chosen.mediaType ?? undefined),
    [shown, chosen.mediaType],
  )
  const scores = useMemo(() => scoreBuckets(shown), [shown])
  const years = useMemo(() => releaseYears(shown), [shown])
  const genres = useMemo(() => countBy(shown, 'genres'), [shown])
  const formats = useMemo(() => countByValue(shown, 'format'), [shown])

  // Anime credits studios and books credit authors; a film's crew lives on its own page
  // rather than in the fields core keeps, so film and TV name nobody here.
  const credits = chosen.mediaType === null ? null : creditKeyFor(chosen.mediaType)
  const credited = useMemo(
    () => (credits === null ? [] : countBy(shown, credits.key)),
    [shown, credits],
  )

  return (
    <AppShell>
      <h1>Stats</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries === null && !error && <p className="muted">Reading your library…</p>}

      {entries !== null && (
        <>
          {/* One shelf at a time, or the whole library added together. */}
          <nav className="lens-bar">
            {LENSES.map((candidate) => (
              <button
                key={candidate.key}
                type="button"
                className={candidate.key === chosen.key ? 'ghost small on' : 'ghost small'}
                aria-pressed={candidate.key === chosen.key}
                onClick={() => setLens(candidate.key)}
              >
                {candidate.label}
              </button>
            ))}
          </nav>

          {shown.length === 0 ? (
            <p className="muted">
              Nothing on this shelf yet. Track something and its figures appear here.
            </p>
          ) : (
            /*
             * Read down: what the shelf amounts to, then how this reader marks it, then what
             * they reach for, then who made it and in what form, and last where in time it
             * comes from. Each answer is narrower than the one above it.
             */
            <>
              <Figures
                figures={[
                  { label: 'entries', value: summary.tracked.toLocaleString() },
                  { label: 'completed', value: summary.completed.toLocaleString() },
                  {
                    label: 'mean score',
                    value: summary.meanScore === null ? '—' : summary.meanScore.toFixed(1),
                    hint: `${summary.rated.toLocaleString()} rated`,
                  },
                  {
                    label: 'spread',
                    value: summary.deviation === null ? '—' : summary.deviation.toFixed(1),
                  },
                  ...(summary.time
                    ? [{ label: summary.time.unit, value: summary.time.amount.toLocaleString() }]
                    : []),
                ]}
              />

              <Bars rows={scores} title="How you score" />

              <Ranked title="What you like" rows={genres} split />

              <div className="stats-pair">
                {credits && <Ranked title={credits.title} rows={credited} />}
                <Ranked title="Formats" rows={formats} asShare />
              </div>

              <Bars rows={years} title="When it's from" />
            </>
          )}
        </>
      )}
    </AppShell>
  )
}
