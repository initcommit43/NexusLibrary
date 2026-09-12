import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AuthNotice } from '../components/AuthNotice'

type State = 'checking' | 'confirmed' | 'expired' | 'failed'

/**
 * Where a confirmation link lands.
 *
 * <p>The token is sent in a request body the moment the page opens and never echoed back into
 * the address bar or the page. It is already in the URL the reader clicked, which is as far as
 * it should travel.
 */
export const VerifyEmailPage = () => {
  const [params] = useSearchParams()
  const token = params.get('token')
  const [state, setState] = useState<State>(token ? 'checking' : 'expired')
  // Development mode mounts effects twice. A link is single-use, so the second attempt would
  // spend nothing and report the first one's success as an expired link.
  const sent = useRef(false)

  useEffect(() => {
    if (!token || sent.current) return
    sent.current = true

    api
      .verifyEmail(token)
      .then(() => setState('confirmed'))
      .catch((err) => setState(err instanceof ApiError && err.status === 410 ? 'expired' : 'failed'))
  }, [token])

  if (state === 'checking') {
    return <AuthNotice title="Confirming your email">One moment.</AuthNotice>
  }

  if (state === 'confirmed') {
    return (
      <AuthNotice title="Email confirmed">
        Your account is ready. <Link to="/login">Sign in</Link>
      </AuthNotice>
    )
  }

  if (state === 'expired') {
    return (
      <AuthNotice title="That link has expired">
        Confirmation links work once and expire after two days. Sign in with your email and
        password and you can ask for a new one. <Link to="/login">Go to sign in</Link>
      </AuthNotice>
    )
  }

  return (
    <AuthNotice title="Something went wrong">
      We could not confirm your email just now. Try the link again in a moment.{' '}
      <Link to="/login">Go to sign in</Link>
    </AuthNotice>
  )
}
