import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AppShell } from '../components/AppShell'

/**
 * Where Trakt sends the browser back to.
 *
 * The code is forwarded to the backend rather than exchanged here, for the same reason
 * AniList's is: the exchange needs the client secret, which has no business in a browser.
 * Posting it from the session that started the flow also keeps the new link bound to that
 * account rather than to whoever happens to open the callback URL.
 */
export const TraktCallbackPage = () => {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [failure, setFailure] = useState<string | null>(null)
  const submitted = useRef(false)

  const code = params.get('code')
  const denied = params.get('error')

  useEffect(() => {
    // React runs effects twice in development; the code may only be exchanged once.
    if (!code || submitted.current) return
    submitted.current = true

    api
      .completeTraktConnect(code)
      .then(() => navigate('/settings', { replace: true }))
      .catch((err) =>
        setFailure(err instanceof ApiError ? err.message : 'Could not complete the Trakt link.'),
      )
  }, [code, navigate])

  const error = denied
    ? 'Trakt did not approve the link.'
    : code
      ? failure
      : 'That link is missing its Trakt authorization code.'

  return (
    <AppShell>
      <h1>Connecting Trakt</h1>
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
        <p className="muted">Finishing the link with Trakt…</p>
      )}
    </AppShell>
  )
}
