import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'

export const AppShell = ({ children }: { children: React.ReactNode }) => {
  const { user, logout } = useAuth()

  return (
    <div className="shell">
      <header className="shell-header">
        <div className="brand">
          <img src="/pwa-192x192.png" alt="" width={32} height={32} />
          <span>Nexus</span>
        </div>

        <nav className="shell-nav">
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/search">Search</NavLink>
          <NavLink to="/settings">Settings</NavLink>
        </nav>

        <div className="header-right">
          <span className="muted">{user?.username}</span>
          <button type="button" className="ghost" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="shell-main">{children}</main>
    </div>
  )
}
