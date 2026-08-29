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
  { key: 'all', label: 'Everything', mediaType: null },
  ...MODULES.flatMap((module) =>
    module.types.map((type) => ({
      key: type.mediaType,
      label: type.label,
      mediaType: type.mediaType,
    })),
  ),
]

/**
 * A count with the mean score of what it counts.
 *
 * <p>The count says what someone watches and the score beside it says what they liked, which
 * is the pair worth reading — either alone is half a sentence. The bar is drawn to the largest
 * count rather than to the page, so the shape is of this list and not of the widest row in it.
 */
const Ranked = ({ title, rows }: { title: string; rows: CountRow[] }) => {
  if (rows.length === 0) return null
  const peak = Math.max(...rows.map((row) => row.amount))

  return (
    <section className="status-section">
      <h2>{title}</h2>
      <ul className="ranked-list">
        {rows.map((row) => (
          <li key={row.label}>
            <span className="ranked-label">{row.label}</span>
            <span className="ranked-track" aria-hidden="true">
              <span
                className="ranked-fill"
                style={{ width: `${Math.round((row.amount / peak) * 100)}%` }}
              />
            </span>
            <span className="ranked-amount">{row.amount.toLocaleString()}</span>
            <span className="ranked-score muted">
              {row.meanScore === null ? '' : row.meanScore.toFixed(1)}
            </span>
          </li>
        ))}
      </ul>
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
          {/* One shelf at a time, or the whole library at once. */}
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
            <>
              <Figures
                figures={[
                  { label: 'tracked', value: summary.tracked.toLocaleString() },
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
                    ? [
                        {
                          label: summary.time.unit,
                          value: summary.time.amount.toLocaleString(),
                        },
                      ]
                    : []),
                ]}
              />

              <Bars rows={scores} title="How you score" />
              <Ranked title="Genres" rows={genres} />
              {credits && <Ranked title={credits.title} rows={credited} />}
              <Bars rows={years} title="Release years" />
              <Ranked title="Formats" rows={formats} />
            </>
          )}
        </>
      )}
    </AppShell>
  )
}
