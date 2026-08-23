import { useCallback, useEffect, useState } from 'react'
import {
  ApiError,
  api,
  type ConnectedAccount,
  type ImportReport,
  type SyncJob,
} from '../api/client'
import { AppShell } from '../components/AppShell'
import { MODULES, type ModuleDefinition, type ModuleProvider } from '../modules/registry'
import { useModules } from '../modules/useModules'

/**
 * Settings spans every module rather than belonging to one: connections sit under the
 * module they feed, so a new module brings its own box instead of a new page.
 */
export const SettingsPage = () => {
  const { isAvailable } = useModules()
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
          <p className="muted">
            {job.state === 'RUNNING'
              ? job.kind === 'IMPORT'
                ? job.total > 0
                  ? `Importing — ${job.processed} of ${job.total} titles…`
                  : 'Fetching your list…'
                : `Syncing achievements — ${job.processed} of ${job.total} games…`
              : job.state === 'COMPLETE'
                ? job.kind === 'IMPORT'
                  ? `Imported ${job.total} titles.`
                  : `Achievements synced — ${job.changed} of ${job.total} games updated.`
                : job.state === 'CANCELLED'
                  ? `Stopped after ${job.processed} of ${job.total}. What was imported was kept.`
                  : job.kind === 'IMPORT'
                    ? 'The import stopped early.'
                    : 'Achievement sync failed.'}
          </p>
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

  const moduleSection = (module: ModuleDefinition) => (
    <section key={module.slug} className="status-section">
      <h2>
        {module.label} {!isAvailable(module.slug) && <span className="muted">— not built yet</span>}
      </h2>

      {module.providers.length === 0 ? (
        <p className="muted">
          Nothing to connect: this module imports from a file rather than an account.
        </p>
      ) : (
        module.providers.map((provider) => {
          if (provider.provider === 'STEAM') return steamCard(provider)
          if (provider.provider === 'ANILIST') return anilistCard(provider)
          return pendingCard(provider)
        })
      )}
    </section>
  )

  return (
    <AppShell>
      <h1>Settings</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {MODULES.map(moduleSection)}
    </AppShell>
  )
}
