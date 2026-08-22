import { useCallback, useEffect, useState } from 'react'
import {
  ApiError,
  api,
  type ConnectedAccount,
  type ImportReport,
} from '../api/client'
import { AppShell } from '../components/AppShell'

export const SettingsPage = () => {
  const [accounts, setAccounts] = useState<ConnectedAccount[] | null>(null)
  const [report, setReport] = useState<ImportReport | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState<'connect' | 'import' | 'disconnect' | null>(null)

  const steam = accounts?.find((account) => account.provider === 'STEAM') ?? null

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
    setBusy('connect')
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

  const runImport = async () => {
    setBusy('import')
    setError(null)
    setReport(null)
    try {
      setReport(await api.importLibrary('STEAM'))
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'The import could not be completed.')
    } finally {
      setBusy(null)
    }
  }

  const disconnectSteam = async () => {
    setBusy('disconnect')
    setError(null)
    try {
      await api.disconnect('STEAM')
      setReport(null)
      load()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not disconnect.')
    } finally {
      setBusy(null)
    }
  }

  return (
    <AppShell>
      <h1>Settings</h1>

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      <section className="status-section">
        <h2>Connections</h2>

        <article className="card integration-card">
          <div className="integration-head">
            <div>
              <h3>Steam</h3>
              <p className="muted">
                {steam
                  ? `Connected as ${steam.externalUserId}`
                  : 'Import your games and playtime.'}
              </p>
            </div>

            {steam ? (
              <div className="integration-actions">
                <button type="button" disabled={busy !== null} onClick={() => void runImport()}>
                  {busy === 'import' ? 'Importing…' : 'Import library'}
                </button>
                <button
                  type="button"
                  className="ghost"
                  disabled={busy !== null}
                  onClick={() => void disconnectSteam()}
                >
                  Disconnect
                </button>
              </div>
            ) : (
              <button type="button" disabled={busy !== null} onClick={() => void connectSteam()}>
                {busy === 'connect' ? 'Redirecting…' : 'Connect Steam'}
              </button>
            )}
          </div>

          <p className="muted note">
            Your Steam profile must have <strong>Game details</strong> set to Public, otherwise
            Steam returns an empty library. Signing in cannot override that setting.
          </p>

          {steam?.lastSyncedAt && (
            <p className="muted">
              Last imported {new Date(steam.lastSyncedAt).toLocaleString()}.
            </p>
          )}
        </article>
      </section>

      {report && (
        <section className="status-section">
          <h2>Import result</h2>
          <p className="muted">
            {report.created} added, {report.updated} updated
            {report.unmatched.length > 0 && `, ${report.unmatched.length} not matched`}.
          </p>

          {report.unmatched.length > 0 && (
            <details className="card unmatched">
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
        </section>
      )}
    </AppShell>
  )
}
