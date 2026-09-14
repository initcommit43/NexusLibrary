import { useCallback, useEffect, useRef, useState } from 'react'
import type { ChangeEvent, ReactNode } from 'react'
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import {
  api,
  errorMessage,
  type ConnectedAccount,
  type ImportReport,
  type MediaType,
  type SyncJob,
} from '../api/client'
import { forgetFinishedJob, useWatchedJob, watchJob as followJob } from '../api/jobs'
import { AppShell } from '../components/AppShell'
import { BackButton } from '../components/BackButton'
import { saveFile } from '../components/download'
import { ChevronRight, Group, GroupRow } from '../components/GroupedList'
import { Toggle } from '../components/Toggle'
import { useNarrowScreen } from '../components/useNarrowScreen'
import {
  MODULES,
  mediaTypesOf,
  type MediaTypeDefinition,
  type ModuleProvider,
  type ModuleSlug,
} from '../modules/registry'
import { useModules } from '../modules/useModules'
import { useAuth } from '../auth/useAuth'
import { useEscapeKey } from '../components/useEscapeKey'

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
    /*
     * A run that stopped at its limit is complete and unfinished at once, and says so in its
     * own words. Told only "done", nobody would press the button again and the rest of their
     * history would never arrive.
     */
    if (job.message) return job.message
    if (isHistory(job)) return `Brought in ${job.processed} ${noun}.`
    return isImport
      ? `Imported ${job.total} titles.`
      : `Achievements synced — ${job.changed} of ${job.total} games updated.`
  }

  if (job.state === 'CANCELLED') {
    if (isHistory(job)) return `Stopped after ${job.processed} ${noun}. What arrived was kept.`
    return `Stopped after ${job.processed} of ${job.total}. What was imported was kept.`
  }

  // Failed, which is a different thing from having reached the limit, and reads as one:
  // the reason comes from the run itself where it had one to give.
  if (job.message) return job.message
  if (isHistory(job)) {
    return `Stopped after ${job.processed} ${noun}. Run it again to carry on from there.`
  }
  return isImport ? 'The import stopped early.' : 'Achievement sync failed.'
}

/** A running job in the few words a connection's row has room for. */
const jobSummary = (job: SyncJob): string => {
  if (isHistory(job)) return `Reading history · ${job.processed} ${HISTORY_NOUNS[job.kind]}`
  if (job.kind !== 'IMPORT') return `Syncing achievements · ${job.processed} of ${job.total}`
  const verb = PHASE_VERBS[job.phase ?? '']
  return verb && job.total > 0 ? `${verb} · ${job.processed} of ${job.total}` : 'Fetching your list'
}

/**
 * How much of a running import a card shows. Steam spells out a failure and animates a run
 * with no total yet; AniList shows only the finished report.
 */
type RunProgress = 'plain' | 'detailed' | 'hidden'

/**
 * What a service needs beyond its registry entry to be connected to.
 *
 * <p>A provider absent from here has no account to link — Goodreads closed its API to new keys
 * in 2020 — and its card offers only the CSV route.
 */
interface Connection {
  /** Asks the backend where to send the reader to approve the link. */
  authorize: () => Promise<{ url: string }>
  /** What to say if we cannot even get that far. */
  failure: string
  /** "Import library" or "Import lists", in the words of what the service holds. */
  importLabel: string
  /** Anything the reader has to do at the other end for the import to return anything. */
  note?: ReactNode
  /** Defaults to plain: a muted status line and a bar once the run has a total. */
  progress?: RunProgress
}

const CONNECTIONS: Partial<Record<ModuleProvider['provider'], Connection>> = {
  STEAM: {
    authorize: api.steamAuthorizeUrl,
    failure: 'Could not start Steam sign-in.',
    importLabel: 'Import library',
    progress: 'detailed',
    note: (
      <>
        Your Steam profile must have <strong>Game details</strong> set to Public, otherwise Steam
        returns an empty library. Signing in cannot override that setting.
      </>
    ),
  },
  ANILIST: {
    authorize: api.anilistAuthorizeUrl,
    failure: 'Could not start the AniList link.',
    importLabel: 'Import lists',
    progress: 'hidden',
  },
  MAL: {
    authorize: api.malAuthorizeUrl,
    failure: 'Could not start the MyAnimeList link.',
    importLabel: 'Import lists',
  },
  SIMKL: {
    authorize: api.simklAuthorizeUrl,
    failure: 'Could not start the Simkl link.',
    importLabel: 'Import library',
  },
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
  const [accountBusy, setAccountBusy] = useState<
    'profile' | 'password' | 'data' | 'delete' | 'clear' | null
  >(null)
  /**
   * Which modules a clear would empty. Everything is ticked to begin with, because that is
   * what someone reaching for this came to do — but it is ticked rather than assumed, so the
   * one destructive act nobody confirms with a password still has to be read before it runs.
   */
  const [clearing, setClearing] = useState<ModuleSlug[]>(() => MODULES.map((m) => m.slug))
  const [accountNote, setAccountNote] = useState<string | null>(null)
  const [reading, setReading] = useState('general')
  const [accounts, setAccounts] = useState<ConnectedAccount[] | null>(null)
  const [report, setReport] = useState<ImportReport | null>(null)
  /**
   * Which provider the run on screen belongs to. Progress and results are page state, but
   * they describe one connection — shown under any other, they say that one is importing.
   */
  const [startedFor, setStartedFor] = useState<ModuleProvider['provider'] | null>(null)
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
  const { job } = useWatchedJob()
  // A run still going when the page opened — started before a reload, or before leaving for
  // another page — belongs under its own card as well as in the dock.
  const runningFor = startedFor ?? (job?.state === 'RUNNING' ? job.provider : null)

  const connected = (provider: ModuleProvider['provider']) =>
    accounts?.find((account) => account.provider === provider) ?? null

  const load = useCallback(() => {
    api
      .listIntegrations()
      .then(setAccounts)
      .catch((err) =>
        setError(errorMessage(err, 'Could not load your connections.')),
      )
  }, [])

  useEffect(load, [load])

  const runImport = async (provider: ModuleProvider['provider']) => {
    setBusy({ provider, action: 'import' })
    setError(null)
    setReport(null)
    forgetFinishedJob()
    setStartedFor(provider)
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
      setError(errorMessage(err, 'The import could not be completed.'))
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
    forgetFinishedJob()
    setStartedFor('ANILIST')
    try {
      const started = await api.importAniListActivity()
      const finished = await watchJob(started.id)
      if (finished?.followUpJobId) {
        await watchJob(finished.followUpJobId)
      }
      load()
    } catch (err) {
      setError(errorMessage(err, 'Your AniList history could not be read.'))
    } finally {
      setBusy(null)
    }
  }

  /**
   * Watches a background job to the end, keeping the latest state on screen.
   *
   * <p>Imports and achievement syncs are minutes of work against someone else's rate limit,
   * so both answer immediately with a job and report progress through it. Without a count
   * on screen there is no way to tell slow from stuck. The watching itself is shared with the
   * dock, so a run called off there stops counting here as well.
   *
   * @return the finished job, so a caller can pick up whatever it started
   */
  const watchJob = async (jobId: string): Promise<SyncJob | null> => {
    const finished = await followJob(jobId)
    if (finished?.state === 'FAILED') {
      setError(finished.message ?? 'That sync could not be completed.')
    }
    return finished
  }

  /**
   * Sends the reader off to approve the link.
   *
   * <p>A full-page navigation rather than a frame or a popup: the approval screen is the
   * provider's own, Steam will not render inside either reliably, and a reader who is about to
   * hand over access to an account should be looking at that provider's address bar.
   */
  const startConnect = async (provider: ModuleProvider['provider'], connection: Connection) => {
    setBusy({ provider, action: 'connect' })
    setError(null)
    try {
      const { url } = await connection.authorize()
      window.location.assign(url)
    } catch (err) {
      setBusy(null)
      setError(errorMessage(err, connection.failure))
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
    forgetFinishedJob()
    setStartedFor(provider)
    try {
      const started = await api.importCsv(provider, file)
      const finished = await watchJob(started.id)
      load()
      if (finished?.report) {
        setReport(finished.report)
      }
    } catch (err) {
      setError(errorMessage(err, 'That file could not be imported.'))
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

  const disconnect = async (provider: ModuleProvider['provider']) => {
    setBusy({ provider, action: 'disconnect' })
    setError(null)
    try {
      await api.disconnect(provider)
      setReport(null)
      load()
    } catch (err) {
      setError(errorMessage(err, 'Could not disconnect.'))
    } finally {
      setBusy(null)
    }
  }

  /** Progress and results for the run this card owns, whichever card that is. */
  const runFeedback = (provider: ModuleProvider, progress: RunProgress = 'plain') => (
    <>
      {progress !== 'hidden' && job && runningFor === provider.provider && (
        <>
          {progress === 'detailed' && job.state === 'FAILED' ? (
            <p className="alert" role="alert">
              {jobLabel(job)}
            </p>
          ) : (
            <p className="muted">{jobLabel(job)}</p>
          )}
          {/* A run with a total fills towards it; one without says only that it is going. */}
          {job.total > 0 ? (
            <div className="achievement-bar">
              <div
                className="achievement-bar-fill"
                style={{ width: `${Math.round((job.processed / job.total) * 100)}%` }}
              />
            </div>
          ) : (
            progress === 'detailed' &&
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
    </>
  )

  /**
   * One card for every service, connected or not.
   *
   * <p>There were five of these, one per provider, and they had drifted: the same run showed a
   * progress bar under Steam and nothing under AniList, and a failed one read as an alert on
   * one card and as a muted line on another. What actually differs between services is small
   * enough to sit in {@link CONNECTIONS} — where the approve screen is, what the import button
   * says, and whether there is anything to warn about — so the card is written once.
   */
  const connectionCard = (provider: ModuleProvider) => {
    const connection = CONNECTIONS[provider.provider]
    const account = connected(provider.provider)

    // Goodreads closed its API to new keys in 2020, so uploading an export is the whole
    // integration there rather than the fallback it is elsewhere: no head buttons, no account.
    // The banner treatment is for a card asking to be connected; a card with nothing to
    // connect to — Goodreads — is not asking for anything and reads as a plain head.
    const head =
      connection && !account ? 'integration-head banner' : 'integration-head'

    return (
      <div key={provider.provider} className="connection">
        <div className={head}>
          <div>
            <h3>{provider.label}</h3>
            <p className="muted">
              {account ? `Connected as ${account.externalUserId}` : provider.blurb}
            </p>
          </div>

          {connection &&
            (account ? (
              <div className="integration-actions">
                <button
                  type="button"
                  disabled={busy !== null}
                  onClick={() => void runImport(provider.provider)}
                >
                  {working(provider.provider, 'import') ? 'Importing…' : connection.importLabel}
                </button>

                {/*
                  * AniList alone keeps a history worth walking, and it is its own press: a
                  * library is what you need before anything else works, and a history is years
                  * deep and worth waiting for separately.
                  */}
                {provider.provider === 'ANILIST' && (
                  <button
                    type="button"
                    className="ghost"
                    disabled={busy !== null}
                    onClick={() => void runAniListActivity()}
                  >
                    {working('ANILIST', 'activity') ? 'Reading…' : 'Import activity'}
                  </button>
                )}

                <button
                  type="button"
                  className="ghost"
                  disabled={busy !== null}
                  onClick={() => void disconnect(provider.provider)}
                >
                  Disconnect
                </button>
              </div>
            ) : (
              <button
                type="button"
                disabled={busy !== null}
                onClick={() => void startConnect(provider.provider, connection)}
              >
                {working(provider.provider, 'connect') ? 'Redirecting…' : `Connect ${provider.label}`}
              </button>
            ))}
        </div>

        {connection?.note && <p className="muted note">{connection.note}</p>}

        {/* The way in that needs no account, for every service that publishes an export. */}
        {provider.csvHint && csvRow(provider)}

        {account?.lastSyncedAt && (
          <p className="muted">Last imported {new Date(account.lastSyncedAt).toLocaleString()}.</p>
        )}

        {runFeedback(provider, connection?.progress)}
      </div>
    )
  }

  const [exporting, setExporting] = useState<MediaType | null>(null)

  const exportShelf = async (type: MediaTypeDefinition) => {
    setExporting(type.mediaType)
    setError(null)
    try {
      const { filename, blob } = await api.exportCsv(type.mediaType)
      saveFile(blob, filename)
    } catch (err) {
      setError(errorMessage(err, 'That shelf could not be exported.'))
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
                </label>
              </li>
            )
          })}
        </ul>
      </article>
    </section>
  )

  const runAccountTask = async (
    task: 'profile' | 'password' | 'data' | 'delete' | 'clear',
    done: string,
    work: () => Promise<string | void>,
  ) => {
    setAccountBusy(task)
    setError(null)
    setAccountNote(null)
    try {
      // A task that counted something says so itself; the rest keep the message they were given.
      const counted = await work()
      setAccountNote(counted ?? done)
    } catch (err) {
      setError(errorMessage(err, 'That did not work. Please try again.'))
    } finally {
      setAccountBusy(null)
    }
  }

  /*
   * Groups of the account card that can also be opened on a screen of their own, where there
   * is no room for the card.
   */
  const profileGroup = () => (
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
  )

  const passwordGroup = () => (
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
  )

  const dataGroup = () => (
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
  )

  const clearGroup = () => (
    <div className="settings-group">
      <h3>Clear your library</h3>

      <p className="danger-note">
        <strong>This permanently deletes the entries on the shelves you choose.</strong> Their
        ratings, progress, notes, reviews and the activity recorded against them go with them.
        Your account, your connected services and your settings are untouched. Nothing is
        archived and nothing can be recovered.
      </p>

      <div className="field-stack">
        <fieldset className="clear-modules">
          <legend>Which shelves</legend>
          {MODULES.map((module) => (
            <label key={module.slug} className="checkbox">
              <input
                type="checkbox"
                checked={clearing.includes(module.slug)}
                onChange={(e) =>
                  setClearing((current) =>
                    e.target.checked
                      ? [...current, module.slug]
                      : current.filter((slug) => slug !== module.slug),
                  )
                }
              />
              <span>{module.label}</span>
            </label>
          ))}
        </fieldset>

        <div className="integration-actions">
          <button
            type="button"
            className="ghost danger"
            disabled={accountBusy !== null || clearing.length === 0}
            onClick={() => {
              const chosen = MODULES.filter((module) => clearing.includes(module.slug))
              const names = chosen.map((module) => module.label).join(', ')
              if (
                !window.confirm(
                  `Permanently delete everything on these shelves?\n\n${names}\n\nThis cannot be undone.`,
                )
              ) {
                return
              }

              void runAccountTask('clear', 'Library cleared.', async () => {
                const removed = await api.clearLibrary(chosen.flatMap(mediaTypesOf))
                return removed.entries === 0
                  ? 'Nothing to clear on those shelves.'
                  : `Cleared ${removed.entries} ${removed.entries === 1 ? 'entry' : 'entries'}.`
              })
            }}
          >
            {accountBusy === 'clear' ? 'Clearing…' : 'Clear these shelves'}
          </button>
        </div>
      </div>
    </div>
  )

  const deleteAccount = () =>
    runAccountTask('delete', 'Account deleted.', async () => {
      await api.deleteAccount(confirmation)
      await logout()
    })

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
        {profileGroup()}
        {passwordGroup()}
        {dataGroup()}

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
                onClick={() => void deleteAccount()}
              >
                {accountBusy === 'delete' ? 'Deleting…' : 'Delete my account'}
              </button>
            </div>
          </div>
        </div>

        {/*
          * Beside deleting the account rather than among the module settings: both are the
          * same kind of act, and someone who has just turned down the larger one should find
          * the narrower alternative directly under it.
          */}
        {clearGroup()}
      </article>
    </section>
  )

  const connectable = MODULES.filter((module) => isEnabled(module.slug)).flatMap(
    (module) => module.providers,
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
        {connectable.map(connectionCard)}
      </article>
    </section>
  )

  /**
   * Every shelf that can leave as a file, in one place rather than one button per module
   * section: taking your lists with you is a thing you do to the whole library at once.
   */
  const shelfExports = () => (
    <>
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
    </>
  )

  const exportSection = () => (
    <section className="settings-section">
      <h2>Export</h2>
      {shelfExports()}
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
   * A phone has no room for the rail and every card at once, so it lists what there is and
   * opens each part on a screen of its own. The screen is in the address rather than in
   * state: the back gesture, the back button and a shared link then all land where they say.
   */
  const narrow = useNarrowScreen()
  const [params] = useSearchParams()
  const section = params.get('section')
  const navigate = useNavigate()
  const { hash, key } = useLocation()

  const subViews: Record<string, { title: string; render: () => ReactNode }> = {
    profile: { title: 'Username and email', render: profileGroup },
    password: { title: 'Password', render: passwordGroup },
    export: {
      title: 'Export your data',
      render: () => (
        <>
          {dataGroup()}
          <div className="settings-group">{shelfExports()}</div>
        </>
      ),
    },
    clear: { title: 'Clear your library', render: clearGroup },
    ...Object.fromEntries(
      connectable.map((provider) => [
        provider.provider.toLowerCase(),
        { title: provider.label, render: () => connectionCard(provider) },
      ]),
    ),
  }

  // Own keys only, so an address naming something like "constructor" gets the list.
  const view = section && Object.hasOwn(subViews, section) ? subViews[section] : undefined

  /** What a connection's row says under its name: a run going on beats how it is linked. */
  const connectionStatus = (provider: ModuleProvider) => {
    if (job?.state === 'RUNNING' && (job.provider ?? runningFor) === provider.provider) {
      return jobSummary(job)
    }
    const account = connected(provider.provider)
    if (account) return `Connected as ${account.externalUserId}`
    if (provider.provider === 'GOODREADS') return 'Import from CSV'
    return accounts === null ? 'Loading…' : 'Not connected'
  }

  const phoneList = () => (
    <>
      {/* The desktop sections' ids, so a link to one still lands on its part of the list. */}
      <div id="general" className="settings-anchor">
        <Group label="Modules">
          {MODULES.map((module) => (
            <GroupRow
              key={module.slug}
              title={module.label}
              trailing={
                <Toggle
                  label={module.label}
                  checked={isEnabled(module.slug)}
                  disabled={!isBuilt(module.slug)}
                  onChange={(on) => void setEnabled(module.slug, on)}
                />
              }
            />
          ))}
        </Group>
        <p className="group-footnote">
          Nothing you have already tracked is deleted, and turning a module back on brings it
          all back.
        </p>
      </div>

      <div id="connections" className="settings-anchor">
        <Group label="Connections">
          {connectable.map((provider) => (
            <GroupRow
              key={provider.provider}
              title={provider.label}
              subtitle={connectionStatus(provider)}
              to={`/settings?section=${provider.provider.toLowerCase()}`}
              trailing={<ChevronRight />}
            />
          ))}
        </Group>
      </div>

      <div id="account" className="settings-anchor">
        <Group label="Account">
          {(['profile', 'password', 'export', 'clear'] as const).map((id) => (
            <GroupRow
              key={id}
              title={subViews[id].title}
              to={`/settings?section=${id}`}
              trailing={<ChevronRight />}
            />
          ))}
          {/* Signed out here, having no account menu on a phone to do it from. */}
          <GroupRow title="Sign out" onClick={() => void logout()} />
          <GroupRow title="Delete account" danger onClick={openDelete} />
        </Group>
      </div>
    </>
  )

  /*
   * The same deletion as the desktop card, password and all, asked as the native alert: the
   * list has no room for the warning and the field standing open under every other row.
   */
  const [deleting, setDeleting] = useState(false)

  const openDelete = () => {
    setError(null)
    setConfirmation('')
    setDeleting(true)
  }

  useEscapeKey(() => setDeleting(false), deleting)

  const deleteDialog = () => (
    // The scrim closes it, as the app's other dialogs do; a tap inside the card must not.
    <div className="dialog-backdrop" role="presentation" onClick={() => setDeleting(false)}>
      <form
        className="dialog dialog-confirm"
        role="dialog"
        aria-modal="true"
        aria-labelledby="delete-account-title"
        onClick={(event) => event.stopPropagation()}
        onSubmit={(event) => {
          event.preventDefault()
          if (confirmation && accountBusy === null) void deleteAccount()
        }}
      >
        <header className="dialog-head">
          <h2 id="delete-account-title">Delete your account?</h2>
        </header>

        <p>
          This permanently deletes your account and everything in it. Nothing is archived and
          nothing can be recovered.
        </p>

        {errorAlert}

        <div className="field-group settings-delete-field">
          <label>
            <input
              type="password"
              autoComplete="new-password"
              aria-label="Confirm with your password"
              placeholder="Password"
              autoFocus
              value={confirmation}
              onChange={(e) => setConfirmation(e.target.value)}
            />
          </label>
        </div>

        <div className="dialog-foot">
          <button type="button" className="ghost" onClick={() => setDeleting(false)}>
            Cancel
          </button>
          <button type="submit" className="danger" disabled={accountBusy !== null || !confirmation}>
            {accountBusy === 'delete' ? 'Deleting…' : 'Delete everything'}
          </button>
        </div>
      </form>
    </div>
  )

  // A message belongs to the screen it was earned on, not to whichever is opened next.
  const [noteOn, setNoteOn] = useState(section)
  if (noteOn !== section) {
    setNoteOn(section)
    setAccountNote(null)
  }

  useEffect(() => {
    if (section) window.scrollTo({ top: 0 })
  }, [section])

  /*
   * A screen opened straight from a link or a reload has nothing behind it, and the back
   * button would leave settings altogether. The list is put behind it once, as if walked.
   */
  const stacked = useRef(false)
  const opensScreen = view !== undefined
  useEffect(() => {
    if (!narrow || !opensScreen || key !== 'default' || stacked.current) return
    stacked.current = true
    navigate('/settings', { replace: true })
    navigate(`/settings?section=${section}`)
  }, [narrow, opensScreen, section, key, navigate])

  // The desktop anchors: export is a screen of its own here, the rest are parts of the list.
  useEffect(() => {
    if (!narrow || section || !hash) return
    if (hash === '#export') navigate('/settings?section=export', { replace: true })
    else document.getElementById(hash.slice(1))?.scrollIntoView()
  }, [narrow, section, hash, navigate])

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
  }, [ids, narrow])

  const csvPicker = (
    <input ref={csvInput} type="file" accept=".csv,text/csv" hidden onChange={onCsvPicked} />
  )

  const errorAlert = error && (
    <p className="alert" role="alert">
      {error}
    </p>
  )

  if (narrow) {
    return (
      <AppShell>
        <BackButton />
        <h1 className="page-title">{view ? view.title : 'Settings'}</h1>
        {csvPicker}
        {/* Said in the dialog while it is open, not behind its scrim as well. */}
        {!deleting && errorAlert}

        {view ? (
          <>
            {accountNote && <p className="muted note">{accountNote}</p>}
            <div className="settings-subview">{view.render()}</div>
          </>
        ) : (
          phoneList()
        )}

        {deleting && deleteDialog()}
      </AppShell>
    )
  }

  return (
    <AppShell>
      <h1 className="page-title">Settings</h1>

      {csvPicker}

      {errorAlert}

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
