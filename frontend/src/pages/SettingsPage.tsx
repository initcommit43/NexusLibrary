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
  type ModuleProvider,
} from '../modules/registry'
import { useModules } from '../modules/useModules'
import { useAuth } from '../auth/useAuth'

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
/**
 * A history walk counts what it has been through rather than what is left.
 *
 * <p>How far back a stream goes is only known by reaching the end of it, so these say a
 * number and not a fraction. A bar that invents a length is a bar that lies twice.
 */
const isHistory = (job: SyncJob) => job.kind === 'ACTIVITY' || job.kind === 'NOTIFICATIONS'

const HISTORY_NOUNS: Record<string, string> = {
  ACTIVITY: 'events',
  NOTIFICATIONS: 'notifications',
}

const jobLabel = (job: SyncJob): string => {
  const isImport = job.kind === 'IMPORT'
  const noun = HISTORY_NOUNS[job.kind ?? ''] ?? 'rows'

  if (job.state === 'RUNNING') {
    if (isHistory(job)) return `Reading your AniList history — ${job.processed} ${noun}…`
    if (!isImport) return `Syncing achievements — ${job.processed} of ${job.total} games…`
    const verb = PHASE_VERBS[job.phase ?? '']
    return verb && job.total > 0
      ? `${verb} — ${job.processed} of ${job.total} titles…`
      : 'Fetching your list…'
  }

  if (job.state === 'COMPLETE') {
    if (isHistory(job)) return `Brought in ${job.processed} ${noun}.`
    return isImport
      ? `Imported ${job.total} titles.`
      : `Achievements synced — ${job.changed} of ${job.total} games updated.`
  }

  if (job.state === 'CANCELLED') {
    if (isHistory(job)) return `Stopped after ${job.processed} ${noun}. What arrived was kept.`
    return `Stopped after ${job.processed} of ${job.total}. What was imported was kept.`
  }

  if (isHistory(job)) {
    return `Stopped after ${job.processed} ${noun}. Run it again to carry on from there.`
  }
  return isImport ? 'The import stopped early.' : 'Achievement sync failed.'
}

/**
 * Settings spans every module rather than belonging to one: connections sit under the
 * module they feed, so a new module brings its own box instead of a new page.
 */
export const SettingsPage = () => {
  const { isAvailable, isBuilt, isEnabled, setEnabled } = useModules()
  const { user, logout, refresh } = useAuth()

  // Seeded from the account rather than left empty: the fields say what they currently
  // are, and this page is only reached once the session has resolved, so they are there.
  const [profile, setProfile] = useState(() => ({
    username: user?.username ?? '',
    email: user?.email ?? '',
  }))
  const [passwords, setPasswords] = useState({ current: '', next: '' })
  const [confirmation, setConfirmation] = useState('')
  const [accountBusy, setAccountBusy] = useState<'profile' | 'password' | 'data' | 'delete' | null>(
    null,
  )
  const [accountNote, setAccountNote] = useState<string | null>(null)
  const [reading, setReading] = useState('general')
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
    action: 'connect' | 'import' | 'disconnect' | 'achievements' | 'activity'
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
   * A reader's AniList history, on its own press.
   *
   * <p>The notification walk is chained behind the activity one server-side, so this follows
   * the follow-up the same way the Steam import follows its achievements.
   */
  const runAniListActivity = async () => {
    setBusy({ provider: 'ANILIST', action: 'activity' })
    setError(null)
    setReport(null)
    setJob(null)
    setRunningFor('ANILIST')
    try {
      const started = await api.importAniListActivity()
      const finished = await watchJob(started.id)
      if (finished?.followUpJobId) {
        await watchJob(finished.followUpJobId)
      }
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Your AniList history could not be read.')
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
    <div key={provider.provider} className="connection">
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
              onClick={() => void runAniListActivity()}
            >
              {working('ANILIST', 'activity') ? 'Reading…' : 'Import AniList activity'}
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
    </div>
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
    <div key={provider.provider} className="connection">
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
          {/* A run with a total fills towards it; one without says only that it is going. */}
          {job.total > 0 ? (
            <div className="achievement-bar">
              <div
                className="achievement-bar-fill"
                style={{ width: `${Math.round((job.processed / job.total) * 100)}%` }}
              />
            </div>
          ) : (
            job.state === 'RUNNING' && (
              <div className="achievement-bar">
                <div className="achievement-bar-fill is-running" />
              </div>
            )
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
    </div>
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
    <div key={provider.provider} className="connection">
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
    </div>
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
    <div key={provider.provider} className="connection">
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
    </div>
  )

  /** Providers whose flow is not built yet, listed so the shape of the app stays visible. */
  const pendingCard = (provider: ModuleProvider) => (
    <div key={provider.provider} className="connection">
      <div className="integration-head">
        <div>
          <h3>{provider.label}</h3>
          <p className="muted">{provider.blurb}</p>
        </div>
        <button type="button" disabled>
          Not built yet
        </button>
      </div>
    </div>
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
    <div key={provider.provider} className="connection">
      <div className="integration-head">
        <div>
          <h3>{provider.label}</h3>
          <p className="muted">{provider.blurb}</p>
        </div>
      </div>

      {csvRow(provider)}
      {runFeedback(provider)}
    </div>
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
      <article className="card">
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

  const runAccountTask = async (
    task: 'profile' | 'password' | 'data' | 'delete',
    done: string,
    work: () => Promise<void>,
  ) => {
    setAccountBusy(task)
    setError(null)
    setAccountNote(null)
    try {
      await work()
      setAccountNote(done)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That did not work. Please try again.')
    } finally {
      setAccountBusy(null)
    }
  }

  /**
   * Who the reader is, how they sign in, and the two rights data protection law gives them.
   * One card: these are four things you do to one account, not four settings that happen to
   * sit near each other.
   */
  const accountSection = () => (
    <section className="settings-section">
      <h2>Account</h2>

      {accountNote && <p className="muted note">{accountNote}</p>}

      <article className="card">
        <div className="settings-group">
          <h3>Profile</h3>
          <p className="muted">Change either and save.</p>

          <div className="field-stack">
            {/*
              * autoComplete off on both: a browser reads a text field beside a password field
              * as a sign-in form and fills it with the saved address, which quietly replaced
              * the username with an email.
              */}
            <label className="field">
              <span>Username</span>
              <input
                type="text"
                autoComplete="off"
                value={profile.username}
                onChange={(e) => setProfile({ ...profile, username: e.target.value })}
              />
            </label>

            <label className="field">
              <span>Email</span>
              <input
                type="email"
                autoComplete="off"
                value={profile.email}
                onChange={(e) => setProfile({ ...profile, email: e.target.value })}
              />
            </label>

            <div className="integration-actions">
              <button
                type="button"
                disabled={
                  accountBusy !== null ||
                  (profile.username === (user?.username ?? '') && profile.email === (user?.email ?? ''))
                }
                onClick={() =>
                  void runAccountTask('profile', 'Saved.', async () => {
                    await api.updateProfile({
                      ...(profile.username === user?.username ? {} : { username: profile.username }),
                      ...(profile.email === user?.email ? {} : { email: profile.email }),
                    })
                    // The header still shows the old name until something reads it again.
                    await refresh()
                  })
                }
              >
                {accountBusy === 'profile' ? 'Saving…' : 'Save'}
              </button>
            </div>
          </div>
        </div>

        <div className="settings-group">
          <h3>Password</h3>
          <p className="muted">
            Your current password is needed as well. Being signed in on this browser is not
            proof it is you.
          </p>

          <div className="field-stack">
            <label className="field">
              <span>Current password</span>
              <input
                type="password"
                autoComplete="new-password"
                value={passwords.current}
                onChange={(e) => setPasswords({ ...passwords, current: e.target.value })}
              />
            </label>

            <label className="field">
              <span>New password</span>
              <input
                type="password"
                autoComplete="new-password"
                value={passwords.next}
                onChange={(e) => setPasswords({ ...passwords, next: e.target.value })}
              />
            </label>

            <div className="integration-actions">
              <button
                type="button"
                disabled={accountBusy !== null || !passwords.current || passwords.next.length < 12}
                onClick={() =>
                  void runAccountTask('password', 'Password changed.', async () => {
                    await api.changePassword(passwords.current, passwords.next)
                    setPasswords({ current: '', next: '' })
                  })
                }
              >
                {accountBusy === 'password' ? 'Changing…' : 'Change password'}
              </button>
            </div>
          </div>
        </div>

        <div className="settings-group">
          <h3>Your data</h3>
          <p className="muted">
            One file with everything this app holds about you: your account, every entry with
            its status, rating, progress, dates and notes, the services you have connected, and
            your activity. Connected services are listed by name only, never with their access
            tokens.
          </p>

          <div className="integration-actions">
            <button
              type="button"
              className="ghost"
              disabled={accountBusy !== null}
              onClick={() =>
                void runAccountTask('data', 'Downloaded.', async () => {
                  const file = await api.exportAccount()
                  saveFile(file.blob, file.filename)
                })
              }
            >
              {accountBusy === 'data' ? 'Preparing…' : 'Download my data'}
            </button>
          </div>
        </div>

        <div className="settings-group">
          <h3>Delete account</h3>

          <p className="danger-note">
            <strong>This permanently deletes your account and everything in it.</strong> Your
            entries, ratings, reviews, notes, activity and connected services are erased at
            once. Nothing is archived and nothing can be recovered, by you or by anyone else.
            If you want a copy, download your data before you do this.
          </p>

          <div className="field-stack">
            <label className="field">
              <span>Confirm with your password</span>
              <input
                type="password"
                autoComplete="new-password"
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
              />
            </label>

            <div className="integration-actions">
              <button
                type="button"
                className="ghost danger"
                disabled={accountBusy !== null || !confirmation}
                onClick={() =>
                  void runAccountTask('delete', 'Account deleted.', async () => {
                    await api.deleteAccount(confirmation)
                    await logout()
                  })
                }
              >
                {accountBusy === 'delete' ? 'Deleting…' : 'Delete my account'}
              </button>
            </div>
          </div>
        </div>
      </article>
    </section>
  )

  /**
   * Every service that can be connected, in one card. Which module a service belongs to is
   * not what anyone is after when they come here to reconnect Steam; module sections come
   * back when a module has settings of its own to hold.
   */
  const connectionsSection = () => (
    <section className="settings-section">
      <h2>Connections</h2>

      <article className="card">
        {MODULES.filter((module) => isEnabled(module.slug)).flatMap((module) =>
          module.providers.map((provider) => {
            if (provider.provider === 'STEAM') return steamCard(provider)
            if (provider.provider === 'ANILIST') return anilistCard(provider)
            if (provider.provider === 'MAL') return malCard(provider)
            if (provider.provider === 'SIMKL') return simklCard(provider)
            if (provider.provider === 'GOODREADS') return goodreadsCard(provider)
            return pendingCard(provider)
          }),
        )}
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
    { id: 'account', label: 'Account', render: accountSection },
    { id: 'connections', label: 'Connections', render: connectionsSection },
    { id: 'export', label: 'Export', render: exportSection },
  ]

  const ids = panes.map((pane) => pane.id).join(',')

  /*
   * The rail marks where the page actually is, not the last thing clicked. The band is a
   * strip just under the header: whatever is crossing it is what is being read, which is
   * what stops the mark sticking to a short section long after it has gone by.
   */
  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        const crossing = entries.find((entry) => entry.isIntersecting)
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
                  className={reading === pane.id ? 'list-link active' : 'list-link'}
                  aria-current={reading === pane.id ? 'true' : undefined}
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
