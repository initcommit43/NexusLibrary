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
      onSubmit={async (v) => {
        await register(v.email ?? '', v.username ?? '', v.password ?? '')
        navigate('/', { replace: true })
      }}
      footer={<>Already registered? <Link to="/login">Sign in</Link></>}
    />
  )
}
