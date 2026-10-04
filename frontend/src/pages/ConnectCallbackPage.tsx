import { useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { ApiError, api, errorMessage, type ConnectedAccount } from '../api/client'
import { AppShell } from '../components/AppShell'

/**
 * Where a provider sends the browser back to.
 *
 * <p>None of the four exchange anything here. AniList, MAL and Simkl all need a client secret
 * — and MAL the PKCE verifier the server minted — neither of which belongs in a browser; Steam
 * needs no secret but redirects without an Authorization header, so its parameters have to be
 * posted from a session that has one. In every case the answer is the same: forward what came
 * back on an authenticated request, which also binds the new link to the session that started
 * the flow rather than to whoever happens to open the callback URL.
 *
 * <p>That made four pages that differed only in a provider's name, which is one page with the
 * name passed in.
 */
const ConnectCallback = ({
  label,
  waiting,
  missing,
  refused,
  failed = `Could not complete the ${label} link.`,
  complete,
}: {
  /** The service's name as a reader knows it, used in every line on the page. */
  label: string
  /** What the page says while the exchange is in flight. */
  waiting: string
  /** What it says when the provider sent us back without the thing we need. */
  missing: string
  /**
   * What it says when the provider reported a refusal, for the flows that can report one.
   * Steam's OpenID response has no such field, so Steam passes none.
   */
  refused?: string
  /** What it says when the exchange itself fails and the server gave no reason. */
  failed?: string
  /** The exchange itself, or null where the URL carries nothing to exchange. */
  complete: (() => Promise<ConnectedAccount>) | null
}) => {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const [failure, setFailure] = useState<string | null>(null)
  const submitted = useRef(false)

  const denied = refused && params.get('error')

  useEffect(() => {
    // React runs effects twice in development; the callback may only be posted once.
    if (!complete || submitted.current) return
    submitted.current = true

    complete()
      .then(() => navigate('/settings', { replace: true }))
      // A refused shape has no field on this page to point at, so it is said in the
      // provider's terms rather than as the forms' "check the highlighted fields".
      .catch((err) =>
        setFailure(
          err instanceof ApiError && Object.keys(err.fieldErrors).length > 0
            ? `${label} sent back a reply this app could not accept. Try connecting again.`
            : errorMessage(err, failed),
        ),
      )
  }, [complete, failed, label, navigate])

  const error = denied ? refused : complete ? failure : missing

  return (
    <AppShell>
      <h1 className="page-title">Connecting {label}</h1>
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
        <p className="muted">{waiting}</p>
      )}
    </AppShell>
  )
}

/**
 * The three OAuth callbacks, which differ only in whose code they forward.
 *
 * The state travels with it, for the two providers that mint one: the server checks that the
 * callback carries the state it handed out when the link was started, which is what keeps a
 * lure link from attaching a stranger's account to the reader's. MAL mints none — its PKCE
 * verifier already binds the callback — and simply ignores the field.
 */
const OAuthCallback = ({
  label,
  exchange,
}: {
  label: string
  exchange: (code: string, state: string | null) => Promise<ConnectedAccount>
}) => {
  const [params] = useSearchParams()
  const code = params.get('code')
  const state = params.get('state')

  const complete = useMemo(
    () => (code ? () => exchange(code, state) : null),
    [code, state, exchange],
  )

  return (
    <ConnectCallback
      label={label}
      waiting={`Finishing the link with ${label}…`}
      missing={`That link is missing its ${label} authorization code.`}
      refused={`${label} did not approve the link.`}
      complete={complete}
    />
  )
}

export const AniListCallbackPage = () => (
  <OAuthCallback label="AniList" exchange={api.completeAniListConnect} />
)

export const MalCallbackPage = () => (
  <OAuthCallback label="MyAnimeList" exchange={api.completeMalConnect} />
)

export const SimklCallbackPage = () => (
  <OAuthCallback label="Simkl" exchange={api.completeSimklConnect} />
)

/**
 * Steam is OpenID rather than OAuth: it hands back a bundle of signed openid.* parameters to
 * verify, not a code to exchange, so it is the one callback that does not fit {@link
 * OAuthCallback}.
 */
export const SteamCallbackPage = () => {
  const [params] = useSearchParams()

  const openIdParams = useMemo(
    () => Object.fromEntries([...params.entries()].filter(([key]) => key.startsWith('openid.'))),
    [params],
  )

  const complete = useMemo(
    () =>
      Object.keys(openIdParams).length > 0 ? () => api.completeSteamConnect(openIdParams) : null,
    [openIdParams],
  )

  return (
    <ConnectCallback
      label="Steam"
      waiting="Verifying with Steam…"
      missing="That link is missing its Steam sign-in details."
      failed="Could not complete the Steam sign-in."
      complete={complete}
    />
  )
}
