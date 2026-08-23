import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { ApiError, api, type TrackedItem } from '../api/client'
import { AppShell } from '../components/AppShell'
import { toDisplayScore } from '../components/rating'
import { MODULES, type ModuleDefinition } from '../modules/registry'
import { useModules } from '../modules/useModules'

/** How many titles a module shows here before you go to its own shelf. */
const PER_MODULE = 6

/**
 * The one screen that spans every module: what you are in the middle of, per medium. The
 * module libraries are for browsing everything; this is for picking up where you left off.
 */
export const DashboardPage = () => {
  const { isAvailable, loading } = useModules()
  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .listEntries()
      .then(setEntries)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your library.'),
      )
  }, [])

  const inModule = (module: ModuleDefinition) =>
    entries?.filter((entry) => module.mediaTypes.includes(entry.mediaType)) ?? []

  const moduleSection = (module: ModuleDefinition) => {
    const mine = inModule(module)
    // In progress first, since that is what a dashboard is for; then whatever else there is.
    const featured = [
      ...mine.filter((entry) => entry.status === 'IN_PROGRESS'),
      ...mine.filter((entry) => entry.status !== 'IN_PROGRESS'),
    ].slice(0, PER_MODULE)

    return (
      <section key={module.slug} className="status-section">
        <h2>
          {module.label} <span className="muted">({mine.length})</span>
          <Link className="section-link" to={`/library/${module.slug}`}>
            Open library
          </Link>
        </h2>

        {featured.length === 0 ? (
          <p className="muted">
            {module.emptyHint}{' '}
            <Link to={`/search?module=${module.slug}`}>{module.searchPlaceholder}</Link>
          </p>
        ) : (
          <div className="cover-grid">
            {featured.map((entry) => (
              <article key={entry.id} className="card cover-card">
                <Link className="cover-link" to={`/entries/${entry.id}`}>
                  {entry.coverUrl ? (
                    <img src={entry.coverUrl} alt="" loading="lazy" />
                  ) : (
                    <div className="cover-placeholder" aria-hidden="true" />
                  )}
                  <div className="cover-heading">
                    <h3>{entry.title}</h3>
                    <p className="muted">
                      {module.statusLabels[entry.status]}
                      {entry.rating !== null && ` · ${toDisplayScore(entry.rating)}`}
                    </p>
                  </div>
                </Link>
              </article>
            ))}
          </div>
        )}
      </section>
    )
  }

  const available = MODULES.filter((module) => isAvailable(module.slug))

  return (
    <AppShell>
      <h1>Dashboard</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {(entries === null || loading) && !error && <p className="muted">Loading your library…</p>}

      {!loading && available.length === 0 && (
        <p className="muted">No modules are available yet.</p>
      )}

      {entries !== null && available.map(moduleSection)}
    </AppShell>
  )
}
