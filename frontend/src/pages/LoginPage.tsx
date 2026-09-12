import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AuthForm } from '../components/AuthForm'
import { AuthNotice } from '../components/AuthNotice'
import { useAuth } from '../auth/useAuth'

/** The server marks a right password on an unconfirmed address with this, not with a message. */
const isUnverified = (err: unknown) =>
  err instanceof ApiError && err.status === 403 && err.fieldErrors.email === 'unverified'

export const LoginPage = () => {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: Location } | null)?.from?.pathname ?? '/'

  const [unconfirmed, setUnconfirmed] = useState<string | null>(null)
  const [resent, setResent] = useState<'idle' | 'sending' | 'sent'>('idle')

  /*
   * Not an error under the email box: the details were right, and the reader has nothing to
   * correct. What they need is the inbox, and a way to ask again if the first mail never came.
   */
  if (unconfirmed) {
    return (
      <AuthNotice title="Confirm your email first">
        Your account is waiting on the link we sent to <strong>{unconfirmed}</strong>. Follow it,
        then sign in.{' '}
        {resent === 'sent' ? (
          'Another link is on its way.'
        ) : (
          <button
            type="button"
            className="link-button"
            disabled={resent === 'sending'}
            onClick={() => {
              setResent('sending')
              // Settles the same whatever the server did with it; there is nothing to report.
              void api
                .resendVerification(unconfirmed)
                .finally(() => setResent('sent'))
            }}
          >
            {resent === 'sending' ? 'Sending…' : 'Send it again'}
          </button>
        )}
      </AuthNotice>
    )
  }

  return (
    <AuthForm
      title="Sign in"
      submitLabel="Sign in"
      fields={[
        { name: 'email', label: 'Email', type: 'email', autoComplete: 'email' },
        { name: 'password', label: 'Password', type: 'password', autoComplete: 'current-password' },
      ]}
      onSubmit={async (v) => {
        try {
          await login(v.email ?? '', v.password ?? '')
        } catch (err) {
          if (isUnverified(err)) {
            setUnconfirmed(v.email ?? '')
            return
          }
          throw err
        }
        navigate(from, { replace: true })
      }}
      footer={
        <>
          <Link to="/forgot-password">Forgot your password?</Link>
          <br />
          No account yet? <Link to="/register">Create one</Link>
        </>
      }
    />
  )
}
