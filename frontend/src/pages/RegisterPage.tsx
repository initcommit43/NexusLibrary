import { Link, useNavigate } from 'react-router-dom'
import { AuthForm } from '../components/AuthForm'
import { useAuth } from '../auth/useAuth'

export const RegisterPage = () => {
  const { register } = useAuth()
  const navigate = useNavigate()

  return (
    <AuthForm
      title="Create account"
      submitLabel="Create account"
      fields={[
        { name: 'email', label: 'Email', type: 'email', autoComplete: 'email' },
        { name: 'username', label: 'Username', type: 'text', autoComplete: 'username' },
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
        await register(v.email ?? '', v.username ?? '', v.password ?? '', true)
        navigate('/', { replace: true })
      }}
      footer={<>Already registered? <Link to="/login">Sign in</Link></>}
    />
  )
}
