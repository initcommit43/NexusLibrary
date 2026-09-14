import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ApiError, api } from '../api/client'
import { AuthForm } from '../components/AuthForm'
import { AuthNotice } from '../components/AuthNotice'
import { useNarrowScreen } from '../components/useNarrowScreen'
import { useAuth } from '../auth/useAuth'

/** The server marks a right password on an unconfirmed address with this, not with a message. */
const isUnverified = (err: unknown) =>
  err instanceof ApiError && err.status === 403 && err.fieldErrors.email === 'unverified'

export const LoginPage = () => {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const narrow = useNarrowScreen()
  const from = (location.state as { from?: Location } | null)?.from?.pathname ?? '/'

  const [unconfirmed, setUnconfirmed] = useState<string | null>(null)
  const [resent, setResent] = useState<'idle' | 'sending' | 'sent'>('idle')

  /*
   * Not an error under the email box: the details were right, and the reader has nothing to
   * correct. What they need is the inbox, and a way to ask again if the first mail never came.
   */
  if (unconfirmed) {
    // Signed in by username, there is no address on the page to show or to send another link to.
    const address = unconfirmed.includes('@') ? unconfirmed : null
    return (
      <AuthNotice
        title="Confirm your email first"
        action={
          !address ? undefined : resent === 'sent' ? (
            'Another link is on its way.'
          ) : (
            <button
              type="button"
              // A phone draws it as the notice's pill, which a bare button already is there.
              className={narrow ? undefined : 'link-button'}
              disabled={resent === 'sending'}
              onClick={() => {
                setResent('sending')
                // Settles the same whatever the server did with it; there is nothing to report.
                void api
                  .resendVerification(address)
                  .finally(() => setResent('sent'))
              }}
            >
              {resent === 'sending' ? 'Sending…' : 'Send it again'}
            </button>
          )
        }
        back={
          <button
            type="button"
            className="link-button"
            onClick={() => {
              setUnconfirmed(null)
              setResent('idle')
            }}
          >
            Back to sign in
          </button>
        }
      >
        Your account is waiting on the link we sent to{' '}
        {address ? <strong>{address}</strong> : 'your email address'}. Follow it, then sign in.
      </AuthNotice>
    )
  }

  return (
    <AuthForm
      title="Sign in"
      submitLabel="Sign in"
      fields={[
        // Either one signs in. 'username' is also what tells a password manager where to fill.
        { name: 'login', label: 'Username or email', type: 'text', autoComplete: 'username' },
        { name: 'password', label: 'Password', type: 'password', autoComplete: 'current-password' },
      ]}
      onSubmit={async (v) => {
        try {
          await login(v.login ?? '', v.password ?? '')
        } catch (err) {
          if (isUnverified(err)) {
            setUnconfirmed(v.login ?? '')
            return
          }
          throw err
        }
        navigate(from, { replace: true })
      }}
      underSubmit={<Link to="/forgot-password">Forgot password?</Link>}
      footer={
        narrow ? (
          <>
            New here? <Link to="/register">Create an account</Link>
          </>
        ) : (
          <>
            <Link to="/forgot-password">Forgot your password?</Link>
            <br />
            No account yet? <Link to="/register">Create one</Link>
          </>
        )
      }
    />
  )
}
