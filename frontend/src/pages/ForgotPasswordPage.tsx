import { useState } from 'react'
import { Link } from 'react-router-dom'
import { AuthForm } from '../components/AuthForm'
import { AuthNotice } from '../components/AuthNotice'
import { api } from '../api/client'

export const ForgotPasswordPage = () => {
  const [sent, setSent] = useState(false)

  // "If that address" rather than "your link is on its way": the server answers the same for
  // an address with no account, and a page that said otherwise would give away what it hides.
  if (sent) {
    return (
      <AuthNotice title="Check your email" action={<Link to="/login">Back to sign in</Link>}>
        If that address has an account, a reset link is on its way. It works for 30 minutes.
      </AuthNotice>
    )
  }

  return (
    <AuthForm
      title="Reset password"
      submitLabel="Send reset link"
      botCheck
      fields={[{ name: 'email', label: 'Email', type: 'email', autoComplete: 'username' }]}
      onSubmit={async (v) => {
        await api.requestPasswordReset(v.email ?? '', v.turnstileToken ?? '')
        setSent(true)
      }}
      footer={
        <>
          Remembered it? <Link to="/login">Sign in</Link>
        </>
      }
    />
  )
}
