import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AuthForm } from '../components/AuthForm'
import { AuthNotice } from '../components/AuthNotice'
import { useNarrowScreen } from '../components/useNarrowScreen'
import { ApiError, api } from '../api/client'

const LIFETIME = 'A reset link works for 30 minutes after it is sent.'

/** Where the mailed link lands. The token rides in the query string and is never shown. */
export const ResetPasswordPage = () => {
  const token = useSearchParams()[0].get('token')
  const [done, setDone] = useState(false)
  const narrow = useNarrowScreen()

  // A link a mail client wrapped mid-URL arrives here with nothing to spend. Say that, rather
  // than showing a form that can only fail once it is filled in.
  if (!token) {
    // A phone gives the way on its own line, as the pill, rather than mid-sentence.
    return narrow ? (
      <AuthNotice
        title="That link is incomplete"
        action={<Link to="/forgot-password">Ask for a new link</Link>}
        back={<Link to="/login">Back to sign in</Link>}
      >
        Open the link from the email exactly as it was sent.
      </AuthNotice>
    ) : (
      <AuthNotice title="That link is incomplete">
        Open the link from the email exactly as it was sent, or{' '}
        <Link to="/forgot-password">ask for a new one</Link>.
      </AuthNotice>
    )
  }

  if (done) {
    return narrow ? (
      <AuthNotice title="Password changed" action={<Link to="/login">Sign in</Link>}>
        Resetting it signed the account out everywhere it was still signed in.
      </AuthNotice>
    ) : (
      <AuthNotice title="Password changed">
        Resetting it signed the account out everywhere it was still signed in.{' '}
        <Link to="/login">Sign in</Link> with the new password.
      </AuthNotice>
    )
  }

  return (
    <AuthForm
      title="Choose a new password"
      submitLabel="Set password"
      fields={[
        { name: 'password', label: 'New password', type: 'password', autoComplete: 'new-password' },
        { name: 'repeat', label: 'Repeat password', type: 'password', autoComplete: 'new-password' },
      ]}
      onSubmit={async (v) => {
        // Thrown as the shape the form already knows how to show, so a typo here reads the
        // same as one the server caught rather than arriving as a different kind of error.
        if (v.password !== v.repeat) {
          throw new ApiError(400, 'Those passwords do not match.', {
            repeat: 'Those passwords do not match.',
          })
        }

        await api.resetPassword(token, v.password ?? '')
        setDone(true)
      }}
      underSubmit={LIFETIME}
      footer={narrow ? <Link to="/login">Back to sign in</Link> : LIFETIME}
    />
  )
}
