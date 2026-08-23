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
    try {
      setReport(await api.importLibrary(provider))
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The import could not be completed.')
    } finally {
      setBusy(null)
    }
  }

  /**
   * Achievements are one Steam request per game, so the server returns a job immediately
   * and this polls it rather than holding a request open for a minute.
   */
  const syncAchievements = async () => {
    setBusy({ provider: 'STEAM', action: 'achievements' })
    setError(null)
    try {
      let current = await api.syncAchievements()
      setJob(current)

      while (current.state === 'RUNNING') {
        await new Promise((resolve) => setTimeout(resolve, 1000))
        current = await api.syncJob(current.id)
        setJob(current)
      }

      if (current.state === 'FAILED') {
        setError(current.message ?? 'The achievement sync failed.')
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not sync achievements.')
    } finally {
      setBusy(null)
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

      {report && (
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
              onClick={() => void syncAchievements()}
            >
              {working('STEAM', 'achievements') ? 'Syncing…' : 'Sync achievements'}
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

      {job && (
        <>
          <p className="muted">
            {job.state === 'RUNNING'
              ? `Checking ${job.processed} of ${job.total} games…`
              : job.state === 'COMPLETE'
                ? `Achievements done — ${job.changed} of ${job.total} games updated.`
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

      {report && (
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
