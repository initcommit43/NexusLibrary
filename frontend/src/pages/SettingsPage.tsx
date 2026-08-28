import { useCallback, useEffect, useRef, useState } from 'react'
import type { ChangeEvent } from 'react'
import {
  ApiError,
  api,
  type ConnectedAccount,
  type ImportReport,
  type MediaType,
  type SyncJob,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { saveFile } from '../components/download'
import {
  MODULES,
  type MediaTypeDefinition,
  type ModuleDefinition,
  type ModuleProvider,
} from '../modules/registry'
import { useModules } from '../modules/useModules'
import { useLocation } from 'react-router-dom'

/**
 * An import runs in three stretches of very different length — pulling the list, matching
 * it against the catalogue, writing the entries — and each counts its own units. Naming
 * the stretch is what stops a bar that restarts from reading as a bar that went backwards.
 */
const PHASE_VERBS: Record<string, string> = {
  MATCHING: 'Matching',
  IMPORTING: 'Importing',
}

/** What a run says about itself, running or finished. */
const jobLabel = (job: SyncJob): string => {
  const isImport = job.kind === 'IMPORT'

  if (job.state === 'RUNNING') {
    if (!isImport) return `Syncing achievements — ${job.processed} of ${job.total} games…`
    const verb = PHASE_VERBS[job.phase ?? '']
    return verb && job.total > 0
      ? `${verb} — ${job.processed} of ${job.total} titles…`
      : 'Fetching your list…'
  }

  if (job.state === 'COMPLETE') {
    return isImport
      ? `Imported ${job.total} titles.`
      : `Achievements synced — ${job.changed} of ${job.total} games updated.`
  }

  if (job.state === 'CANCELLED') {
    return `Stopped after ${job.processed} of ${job.total}. What was imported was kept.`
  }

  return isImport ? 'The import stopped early.' : 'Achievement sync failed.'
}

/**
 * Settings spans every module rather than belonging to one: connections sit under the
 * module they feed, so a new module brings its own box instead of a new page.
 */
export const SettingsPage = () => {
  const { isAvailable, isBuilt, isEnabled, setEnabled } = useModules()
  const { hash } = useLocation()
  const [accounts, setAccounts] = useState<ConnectedAccount[] | null>(null)
  const [report, setReport] = useState<ImportReport | null>(null)
  /**
   * Which provider the run on screen belongs to. Progress and results are page state, but
   * they describe one connection — shown under any other, they say that one is importing.
   */
  const [runningFor, setRunningFor] = useState<ModuleProvider['provider'] | null>(null)
  const [error, setError] = useState<string | null>(null)
  /**
   * Which provider is working, and at what. One flag for the whole page made every card
   * claim to be importing whenever any of them was.
   */
  const [busy, setBusy] = useState<{
    provider: ModuleProvider['provider']
    action: 'connect' | 'import' | 'disconnect' | 'achievements'
  } | null>(null)

  const working = (provider: ModuleProvider['provider'], action?: string) =>
    busy?.provider === provider && (action === undefined || busy.action === action)
  const [job, setJob] = useState<SyncJob | null>(null)

  const connected = (provider: ModuleProvider['provider']) =>
    accounts?.find((account) => account.provider === provider) ?? null

  const steam = connected('STEAM')
  const anilist = connected('ANILIST')
  const mal = connected('MAL')
  const simkl = connected('SIMKL')

  const load = useCallback(() => {
    api
      .listIntegrations()
      .then(setAccounts)
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your connections.'),
      )
  }, [])

  useEffect(load, [load])

  const connectSteam = async () => {
    setBusy({ provider: 'STEAM', action: 'connect' })
    setError(null)
    try {
      const { url } = await api.steamAuthorizeUrl()
      // Full-page navigation: Steam will not render inside a frame or a popup reliably.
      window.location.href = url
    } catch (err) {
      setBusy(null)
      setError(err instanceof ApiError ? err.message : 'Could not start Steam sign-in.')
    }
  }

  const runImport = async (provider: ModuleProvider['provider']) => {
    setBusy({ provider, action: 'import' })
    setError(null)
    setReport(null)
    setJob(null)
    setRunningFor(provider)
    try {
      const started = await api.importLibrary(provider)
      const finished = await watchJob(started.id)
      load()

      if (finished?.report) {
        setReport(finished.report)
      }
      // Steam follows an import with its achievements; watching that keeps the count moving.
      if (finished?.followUpJobId) {
        await watchJob(finished.followUpJobId)
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The import could not be completed.')
    } finally {
      setBusy(null)
    }
  }

  /**
   * Watches a background job to the end, keeping the latest state on screen.
   *
   * <p>Imports and achievement syncs are minutes of work against someone else's rate limit,
   * so both answer immediately with a job and report progress through it. Without a count
   * on screen there is no way to tell slow from stuck.
   *
   * @return the finished job, so a caller can pick up whatever it started
   */
  const watchJob = async (jobId: string): Promise<SyncJob | null> => {
    try {
      let current = await api.syncJob(jobId)
      setJob(current)

      while (current.state === 'RUNNING') {
        await new Promise((resolve) => setTimeout(resolve, 1000))
        current = await api.syncJob(jobId)
        setJob(current)
      }

      if (current.state === 'FAILED') {
        setError(current.message ?? 'That sync could not be completed.')
      }
      return current
    } catch {
      // The work carries on server-side; losing sight of it is not worth an alarm.
      return null
    }
  }

  const connectAniList = async () => {
    setBusy({ provider: 'ANILIST', action: 'connect' })
    setError(null)
    try {
      const { url } = await api.anilistAuthorizeUrl()
      // Full-page navigation: the approval screen is AniList's, not ours to embed.
      window.location.href = url
    } catch (err) {
      setBusy(null)
      setError(err instanceof ApiError ? err.message : 'Could not start the AniList link.')
    }
  }

  /**
   * One file input for the whole page rather than one per card. The picker is the same
   * dialog whichever card opened it, and which provider asked is a fact about the click,
   * not about the markup.
   */
  const csvInput = useRef<HTMLInputElement | null>(null)
  const [csvProvider, setCsvProvider] = useState<ModuleProvider['provider'] | null>(null)

  const pickCsv = (provider: ModuleProvider['provider']) => {
    setError(null)
    setCsvProvider(provider)
    csvInput.current?.click()
  }

  const onCsvPicked = (event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    // Cleared straight away, so picking the same file twice in a row still fires a change.
    event.target.value = ''
    if (file && csvProvider) void runCsvImport(csvProvider, file)
  }

  /** The upload route, watched exactly like the account import it stands in for. */
  const runCsvImport = async (provider: ModuleProvider['provider'], file: File) => {
    setBusy({ provider, action: 'import' })
    setError(null)
    setReport(null)
    setJob(null)
    setRunningFor(provider)
    try {
      const started = await api.importCsv(provider, file)
      const finished = await watchJob(started.id)
      load()
      if (finished?.report) {
        setReport(finished.report)
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That file could not be imported.')
    } finally {
      setBusy(null)
    }
  }

  /**
   * The way in that needs no account: upload what the service exported. Sits under the
   * connection buttons on every card, because it is the alternative to using them.
   */
  const csvRow = (provider: ModuleProvider) => (
    <div className="integration-csv">
      <button
        type="button"
        className="ghost"
        disabled={busy !== null}
        onClick={() => pickCsv(provider.provider)}
      >
        {working(provider.provider, 'import') ? 'Importing…' : 'Import from CSV'}
      </button>
      {provider.csvHint && <span className="muted csv-hint">{provider.csvHint}</span>}
    </div>
  )

  const anilistCard = (provider: ModuleProvider) => (
    <article key={provider.provider} className="card integration-card">
      <div className={anilist ? 'integration-head' : 'integration-head banner'}>
        <div>
          <h3>{provider.label}</h3>
          <p className="muted">
            {anilist ? `Connected as ${anilist.externalUserId}` : provider.blurb}
          </p>
        </div>

        {anilist ? (
          <div className="integration-actions">
            <button type="button" disabled={busy !== null} onClick={() => void runImport('ANILIST')}>
              {working('ANILIST', 'import') ? 'Importing…' : 'Import lists'}
            </button>
            <button
              type="button"
              className="ghost"
              disabled={busy !== null}
              onClick={() => void disconnect('ANILIST')}
            >
              Disconnect
            </button>
          </div>
        ) : (
          <button type="button" disabled={busy !== null} onClick={() => void connectAniList()}>
            {working('ANILIST', 'connect') ? 'Redirecting…' : 'Connect AniList'}
          </button>
        )}
      </div>

      {csvRow(provider)}

      {anilist?.lastSyncedAt && (
        <p className="muted">Last imported {new Date(anilist.lastSyncedAt).toLocaleString()}.</p>
      )}

      {report && runningFor === provider.provider && (
        <>
          <p className="muted">
            {report.created} added, {report.updated} updated
            {report.unmatched.length > 0 && `, ${report.unmatched.length} not matched`}.
          </p>

          {report.unmatched.length > 0 && (
            <details className="unmatched">
              <summary>Titles we could not match ({report.unmatched.length})</summary>
              <ul>
                {report.unmatched.map((item) => (
                  <li key={item.providerItemId}>
                    {item.title} <span className="muted">— {item.reason}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </>
      )}
    </article>
  )

  const disconnect = async (provider: ModuleProvider['provider']) => {
    setBusy({ provider, action: 'disconnect' })
    setError(null)
    try {
      await api.disconnect(provider)
      setReport(null)
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not disconnect.')
    } finally {
      setBusy(null)
    }
  }

  const steamCard = (provider: ModuleProvider) => (
    <article key={provider.provider} className="card integration-card">
      <div className={steam ? 'integration-head' : 'integration-head banner'}>
        <div>
          <h3>{provider.label}</h3>
          <p className="muted">{steam ? `Connected as ${steam.externalUserId}` : provider.blurb}</p>
        </div>

        {steam ? (
          <div className="integration-actions">
            <button type="button" disabled={busy !== null} onClick={() => void runImport('STEAM')}>
              {working('STEAM', 'import') ? 'Importing…' : 'Import library'}
            </button>
            <button
              type="button"
              className="ghost"
              disabled={busy !== null}
              onClick={() => void disconnect('STEAM')}
            >
              Disconnect
            </button>
          </div>
        ) : (
          <button type="button" disabled={busy !== null} onClick={() => void connectSteam()}>
            {working('STEAM', 'connect') ? 'Redirecting…' : 'Connect Steam'}
          </button>
        )}
      </div>

      <p className="muted note">
        Your Steam profile must have <strong>Game details</strong> set to Public, otherwise Steam
        returns an empty library. Signing in cannot override that setting.
      </p>

      {steam?.lastSyncedAt && (
        <p className="muted">Last imported {new Date(steam.lastSyncedAt).toLocaleString()}.</p>
      )}

      {job && runningFor === provider.provider && (
        <>
          <p className="muted">{jobLabel(job)}</p>
          {job.total > 0 && (
            <div className="achievement-bar">
              <div
                className="achievement-bar-fill"
                style={{ width: `${Math.round((job.processed / job.total) * 100)}%` }}
              />
            </div>
          )}
        </>
      )}

      {report && runningFor === provider.provider && (
        <>
          <p className="muted">
            {report.created} added, {report.updated} updated
            {report.unmatched.length > 0 && `, ${report.unmatched.length} not matched`}.
          </p>

          {report.unmatched.length > 0 && (
            <details className="unmatched">
              <summary>Titles we could not match ({report.unmatched.length})</summary>
              <ul>
                {report.unmatched.map((item) => (
                  <li key={item.providerItemId}>
                    {item.title} <span className="muted">— {item.reason}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </>
      )}
    </article>
  )

  const connectMal = async () => {
    setBusy({ provider: 'MAL', action: 'connect' })
    setError(null)
    try {
      const { url } = await api.malAuthorizeUrl()
      // Full-page navigation: the approval screen is MAL's, not ours to embed.
      window.location.href = url
    } catch (err) {
      setBusy(null)
      setError(err instanceof ApiError ? err.message : 'Could not start the MyAnimeList link.')
    }
  }

  const malCard = (provider: ModuleProvider) => (
    <article key={provider.provider} className="card integration-card">
      <div className={mal ? 'integration-head' : 'integration-head banner'}>
        <div>
          <h3>{provider.label}</h3>
          <p className="muted">{mal ? `Connected as ${mal.externalUserId}` : provider.blurb}</p>
        </div>

        {mal ? (
          <div className="integration-actions">
            <button type="button" disabled={busy !== null} onClick={() => void runImport('MAL')}>
              {working('MAL', 'import') ? 'Importing…' : 'Import lists'}
            </button>
            <button
              type="button"
              className="ghost"
              disabled={busy !== null}
              onClick={() => void disconnect('MAL')}
            >
              Disconnect
            </button>
          </div>
        ) : (
          <button type="button" disabled={busy !== null} onClick={() => void connectMal()}>
            {working('MAL', 'connect') ? 'Redirecting…' : 'Connect MyAnimeList'}
          </button>
        )}
      </div>

      {csvRow(provider)}

      {mal?.lastSyncedAt && (
        <p className="muted">Last imported {new Date(mal.lastSyncedAt).toLocaleString()}.</p>
      )}

      {job && runningFor === provider.provider && (
        <>
          <p className="muted">{jobLabel(job)}</p>
          {job.total > 0 && (
            <div className="achievement-bar">
              <div
                className="achievement-bar-fill"
                style={{ width: `${Math.round((job.processed / job.total) * 100)}%` }}
              />
            </div>
          )}
        </>
      )}

      {report && runningFor === provider.provider && (
        <>
          <p className="muted">
            {report.created} added, {report.updated} updated
            {report.unmatched.length > 0 && `, ${report.unmatched.length} not matched`}.
          </p>

          {report.unmatched.length > 0 && (
            <details className="unmatched">
              <summary>Titles we could not match ({report.unmatched.length})</summary>
              <ul>
                {report.unmatched.map((item) => (
                  <li key={item.providerItemId}>
                    {item.title} <span className="muted">— {item.reason}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </>
      )}
    </article>
  )

  const connectSimkl = async () => {
    setBusy({ provider: 'SIMKL', action: 'connect' })
    setError(null)
    try {
      const { url } = await api.simklAuthorizeUrl()
      // Full-page navigation: the approval screen is Simkl's, not ours to embed.
      window.location.href = url
    } catch (err) {
      setBusy(null)
      setError(err instanceof ApiError ? err.message : 'Could not start the Simkl link.')
    }
  }

  const simklCard = (provider: ModuleProvider) => (
    <article key={provider.provider} className="card integration-card">
      <div className={simkl ? 'integration-head' : 'integration-head banner'}>
        <div>
          <h3>{provider.label}</h3>
          <p className="muted">
            {simkl ? `Connected as ${simkl.externalUserId}` : provider.blurb}
          </p>
        </div>

        {simkl ? (
          <div className="integration-actions">
            <button type="button" disabled={busy !== null} onClick={() => void runImport('SIMKL')}>
              {working('SIMKL', 'import') ? 'Importing…' : 'Import library'}
            </button>
            <button
              type="button"
              className="ghost"
              disabled={busy !== null}
              onClick={() => void disconnect('SIMKL')}
            >
              Disconnect
            </button>
          </div>
        ) : (
          <button type="button" disabled={busy !== null} onClick={() => void connectSimkl()}>
            {working('SIMKL', 'connect') ? 'Redirecting…' : 'Connect Simkl'}
          </button>
        )}
      </div>

      {csvRow(provider)}

      {simkl?.lastSyncedAt && (
        <p className="muted">Last imported {new Date(simkl.lastSyncedAt).toLocaleString()}.</p>
      )}

      {job && runningFor === provider.provider && (
        <>
          <p className="muted">{jobLabel(job)}</p>
          {job.total > 0 && (
            <div className="achievement-bar">
              <div
                className="achievement-bar-fill"
                style={{ width: `${Math.round((job.processed / job.total) * 100)}%` }}
              />
            </div>
          )}
        </>
      )}

      {report && runningFor === provider.provider && (
        <>
          <p className="muted">
            {report.created} added, {report.updated} updated
            {report.unmatched.length > 0 && `, ${report.unmatched.length} not matched`}.
          </p>

          {report.unmatched.length > 0 && (
            <details className="unmatched">
              <summary>Titles we could not match ({report.unmatched.length})</summary>
              <ul>
                {report.unmatched.map((item) => (
                  <li key={item.providerItemId}>
                    {item.title} <span className="muted">— {item.reason}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </>
      )}
    </article>
  )

  /** Providers whose flow is not built yet, listed so the shape of the app stays visible. */
  const pendingCard = (provider: ModuleProvider) => (
    <article key={provider.provider} className="card integration-card">
      <div className="integration-head">
        <div>
          <h3>{provider.label}</h3>
          <p className="muted">{provider.blurb}</p>
        </div>
        <button type="button" disabled>
          Not built yet
        </button>
      </div>
    </article>
  )

  /**
   * Progress and results for the run this card owns. The connected-account cards each carry
   * their own copy of this from before there was a card without a connection to show.
   */
  const runFeedback = (provider: ModuleProvider) => (
    <>
      {job && runningFor === provider.provider && (
        <>
          <p className="muted">{jobLabel(job)}</p>
          {job.total > 0 && (
            <div className="achievement-bar">
              <div
                className="achievement-bar-fill"
                style={{ width: `${Math.round((job.processed / job.total) * 100)}%` }}
              />
            </div>
          )}
        </>
      )}

      {report && runningFor === provider.provider && (
        <>
          <p className="muted">
            {report.created} added, {report.updated} updated
            {report.unmatched.length > 0 && `, ${report.unmatched.length} not matched`}.
          </p>

          {report.unmatched.length > 0 && (
            <details className="unmatched">
              <summary>Titles we could not match ({report.unmatched.length})</summary>
              <ul>
                {report.unmatched.map((item) => (
                  <li key={item.providerItemId}>
                    {item.title} <span className="muted">— {item.reason}</span>
                  </li>
                ))}
              </ul>
            </details>
          )}
        </>
      )}
    </>
  )

  /**
   * The only card with no connection to make: Goodreads closed its API to new keys in 2020,
   * so uploading an export is the whole integration rather than the fallback it is elsewhere.
   */
  const goodreadsCard = (provider: ModuleProvider) => (
    <article key={provider.provider} className="card integration-card">
      <div className="integration-head">
        <div>
          <h3>{provider.label}</h3>
          <p className="muted">{provider.blurb}</p>
        </div>
      </div>

      {csvRow(provider)}
      {runFeedback(provider)}
    </article>
  )

  const [exporting, setExporting] = useState<MediaType | null>(null)

  const exportShelf = async (type: MediaTypeDefinition) => {
    setExporting(type.mediaType)
    setError(null)
    try {
      const { filename, blob } = await api.exportCsv(type.mediaType)
      saveFile(blob, filename)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That shelf could not be exported.')
    } finally {
      setExporting(null)
    }
  }

  /**
   * Which modules this reader wants at all.
   *
   * <p>First, because it decides what the rest of the page is even about: there is no reason
   * to read about connecting Steam on an account that has switched games off.
   */
  const generalSection = () => (
    <section className="settings-section">
      <h2>Modules</h2>
      <article className="card integration-card">
        <p className="muted">
          Switch off what you do not track. A module you turn off leaves the navigation and its
          shelves entirely; nothing you have already tracked is deleted, and turning it back on
          brings it all back.
        </p>

        <ul className="switch-list">
          {MODULES.map((module) => {
            const built = isBuilt(module.slug)
            return (
              <li key={module.slug}>
                <label className={built ? 'switch-row' : 'switch-row disabled'}>
                  <input
                    type="checkbox"
                    checked={isEnabled(module.slug)}
                    disabled={!built}
                    onChange={(event) => void setEnabled(module.slug, event.target.checked)}
                  />
                  <span>{module.label}</span>
                  {!built && <span className="muted">Not built yet</span>}
                </label>
              </li>
            )
          })}
        </ul>
      </article>
    </section>
  )

  /**
   * Every shelf that can leave as a file, in one place rather than one button per module
   * section: taking your lists with you is a thing you do to the whole library at once.
   */
  const exportSection = () => (
    <section className="settings-section">
      <h2>Export</h2>
      <p className="muted">
        One file per shelf, with everything this app knows about each entry — status, rating,
        progress, dates and notes. Opens in any spreadsheet.
      </p>

      <div className="export-row">
        {MODULES.filter((module) => module.exportsCsv && isAvailable(module.slug))
          .flatMap((module) => module.types)
          .map((type) => (
            <button
              key={type.mediaType}
              type="button"
              className="ghost"
              disabled={exporting !== null}
              onClick={() => void exportShelf(type)}
            >
              {exporting === type.mediaType ? 'Preparing…' : `${type.label} CSV`}
            </button>
          ))}
      </div>
    </section>
  )

  const moduleSection = (module: ModuleDefinition) => (
    <section key={module.slug} className="settings-section">
      {/* The rail already names the module; this names what the pane is about. */}
      <h2>
        Connections {!isBuilt(module.slug) && <span className="muted">— not built yet</span>}
      </h2>

      {module.providers.length === 0 ? (
        <p className="muted">
          Nothing to connect: this module imports from a file rather than an account.
        </p>
      ) : (
        module.providers.map((provider) => {
          if (provider.provider === 'STEAM') return steamCard(provider)
          if (provider.provider === 'ANILIST') return anilistCard(provider)
          if (provider.provider === 'MAL') return malCard(provider)
          if (provider.provider === 'SIMKL') return simklCard(provider)
          if (provider.provider === 'GOODREADS') return goodreadsCard(provider)
          return pendingCard(provider)
        })
      )}
    </section>
  )

  /*
   * Every section is on the page; the rail is a way down it rather than a set of tabs. The
   * groups are the same shape as a shelf's own rail, and named the same way, because they
   * are the same idea: a short list of everything there is, with a way to each.
   *
   * Only modules still switched on appear — connecting an account to a shelf nobody has is
   * a row of settings for something that is not on screen anywhere else.
   */
  const panes = [
    { id: 'general', label: 'General', render: generalSection },
    ...MODULES.filter((module) => isEnabled(module.slug)).map((module) => ({
      id: module.slug,
      label: module.label,
      render: () => moduleSection(module),
    })),
    { id: 'export', label: 'Export', render: exportSection },
  ]

  return (
    <AppShell>
      <h1>Settings</h1>

      <input
        ref={csvInput}
        type="file"
        accept=".csv,text/csv"
        hidden
        onChange={onCsvPicked}
      />

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      <div className="list-layout">
        <aside className="list-sidebar">
          <ul className="list-links">
            {panes.map((pane) => (
              <li key={pane.id}>
                <a
                  className={hash === `#${pane.id}` ? 'list-link active' : 'list-link'}
                  href={`#${pane.id}`}
                >
                  {pane.label}
                </a>
              </li>
            ))}
          </ul>
        </aside>

        <div className="list-main">
          {panes.map((pane) => (
            <div key={pane.id} id={pane.id} className="settings-anchor">
              {pane.render()}
            </div>
          ))}
        </div>
      </div>
    </AppShell>
  )
}
