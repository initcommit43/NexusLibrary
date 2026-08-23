import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AppShell } from '../components/AppShell'

/**
 * Where AniList sends the browser back to.
 *
 * The code is forwarded to the backend on an authenticated request rather than exchanged
 * here: the exchange needs the client secret, which must never reach a browser, and posting
 * it from the session that started the flow keeps the new link bound to that account.
 */
export const AniListCallbackPage = () => {
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
      .completeAniListConnect(code)
      .then(() => navigate('/settings', { replace: true }))
      .catch((err) =>
        setFailure(err instanceof ApiError ? err.message : 'Could not complete the AniList link.'),
      )
  }, [code, navigate])

  const error = denied
    ? 'AniList did not approve the link.'
    : code
      ? failure
      : 'That link is missing its AniList authorization code.'

  return (
    <AppShell>
      <h1>Connecting AniList</h1>
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
        <p className="muted">Finishing the link with AniList…</p>
      )}
    </AppShell>
  )
}
