import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export const ProtectedRoute = () => {
  const { status } = useAuth()
  const location = useLocation()

  if (status === 'loading') return <div className="page-centered">Loading…</div>
  if (status === 'anonymous') return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}
