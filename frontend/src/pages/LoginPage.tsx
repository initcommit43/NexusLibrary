import { Link, useLocation, useNavigate } from 'react-router-dom'
import { AuthForm } from '../components/AuthForm'
import { useAuth } from '../auth/useAuth'

export const LoginPage = () => {
  const { login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: Location } | null)?.from?.pathname ?? '/'

  return (
    <AuthForm
      title="Sign in"
      submitLabel="Sign in"
      fields={[
        { name: 'email', label: 'Email', type: 'email', autoComplete: 'email' },
        { name: 'password', label: 'Password', type: 'password', autoComplete: 'current-password' },
      ]}
      onSubmit={async (v) => {
        await login(v.email ?? '', v.password ?? '')
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
