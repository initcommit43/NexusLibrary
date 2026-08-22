import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AppShell } from '../components/AppShell'

/**
 * Where Steam sends the browser back to.
 *
 * Steam redirects without an Authorization header, so the parameters are forwarded to the
 * backend on an authenticated request from here instead. That keeps the link bound to the
 * session that started it rather than to whoever happens to open this URL.
 */
export const SteamCallbackPage = () => {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [failure, setFailure] = useState<string | null>(null)
  const submitted = useRef(false)

  const openIdParams = useMemo(
    () => Object.fromEntries([...params.entries()].filter(([key]) => key.startsWith('openid.'))),
    [params],
  )
  const hasOpenIdParams = Object.keys(openIdParams).length > 0

  useEffect(() => {
    // React runs effects twice in development; the callback must only be posted once.
    if (!hasOpenIdParams || submitted.current) return
    submitted.current = true

    api
      .completeSteamConnect(openIdParams)
      .then(() => navigate('/settings', { replace: true }))
      .catch((err) =>
        setFailure(err instanceof ApiError ? err.message : 'Could not complete the Steam sign-in.'),
      )
  }, [hasOpenIdParams, openIdParams, navigate])

  const error = hasOpenIdParams ? failure : 'That link is missing its Steam sign-in details.'

  return (
    <AppShell>
      <h1>Connecting Steam</h1>
      {error ? (
        <>
          <p className="alert" role="alert">
            {error}
          </p>
          <button type="button" onClick={() => navigate('/settings', { replace: true })}>
            Back to settings
          </button>
        </>
      ) : (
        <p className="muted">Verifying with Steam…</p>
      )}
    </AppShell>
  )
}
