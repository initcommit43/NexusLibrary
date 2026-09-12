import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { AuthForm } from '../components/AuthForm'
import { AuthNotice } from '../components/AuthNotice'
import { useAuth } from '../auth/useAuth'

export const RegisterPage = () => {
  const { register } = useAuth()
  const navigate = useNavigate()
  const [awaiting, setAwaiting] = useState<string | null>(null)

  /*
   * The account exists but cannot be opened yet. Said plainly, with where the mail went, because
   * the reader's next move is in another app entirely and they need to know which inbox to open.
   */
  if (awaiting) {
    return (
      <AuthNotice title="Check your email">
        We sent a confirmation link to <strong>{awaiting}</strong>. Follow it, then sign in. It can
        take a minute to arrive, and it is worth checking your spam folder.{' '}
        <Link to="/login">Go to sign in</Link>
      </AuthNotice>
    )
  }

  return (
    <AuthForm
      title="Create account"
      submitLabel="Create account"
      botCheck
      fields={[
        // Sign-in is by email, so the email is the credential's name: 'username' is what tells a
        // password manager which field to save beside the password. Put on the display name, it
        // saved that instead, and then filled it into the email box on every sign-in.
        { name: 'email', label: 'Email', type: 'email', autoComplete: 'username' },
        { name: 'username', label: 'Username', type: 'text', autoComplete: 'nickname' },
        { name: 'password', label: 'Password', type: 'password', autoComplete: 'new-password' },
      ]}
      consent={
        // New tab, both of them. Following either in place unmounts this form and takes the
        // half-filled email, username and password with it — so the reader is punished for
        // doing the one thing the sentence asks them to do.
        <>
          I have read and accept the{' '}
          <Link to="/terms" target="_blank" rel="noreferrer">
            Terms of Service
          </Link>{' '}
          and the{' '}
          <Link to="/privacy" target="_blank" rel="noreferrer">
            Privacy Policy
          </Link>
          .
        </>
      }
      onSubmit={async (v) => {
        // True by construction: the button is disabled until the box is ticked. Sent
        // anyway, and checked again on the server, because a client is not where a
        // requirement like this can be allowed to live.
        const outcome = await register(
          v.email ?? '',
          v.username ?? '',
          v.password ?? '',
          true,
          v.turnstileToken ?? '',
        )
        if (outcome === 'confirm-email') {
          setAwaiting(v.email ?? '')
          return
        }
        navigate('/', { replace: true })
      }}
      footer={<>Already registered? <Link to="/login">Sign in</Link></>}
    />
  )
}
