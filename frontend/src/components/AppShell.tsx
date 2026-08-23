import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ModuleSwitcher } from './ModuleSwitcher'
import { ThemeToggle } from './ThemeToggle'
import type { ModuleDefinition } from '../modules/registry'

/**
 * Pages that belong to a module pass it in, which names it in the switcher; the pages that
 * span every module leave it out rather than claiming one you are not looking at.
 */
export const AppShell = ({
  children,
  module,
}: {
  children: React.ReactNode
  module?: ModuleDefinition
}) => {
  const { user, logout } = useAuth()

  return (
    <div className="shell">
      <header className="shell-header">
        <div className="brand">
          <img src="/pwa-192x192.png" alt="" width={28} height={28} />
          <span>Nexus</span>
        </div>

        {module && <ModuleSwitcher current={module} />}

        <nav className="shell-nav">
          <NavLink to={module ? `/library/${module.slug}` : '/'} end>
            Dashboard
          </NavLink>
          <NavLink to="/search">Search</NavLink>
          <NavLink to="/activity">Activity</NavLink>
          <NavLink to="/settings">Settings</NavLink>
        </nav>

        <div className="header-right">
          <span className="muted">{user?.username}</span>
          <ThemeToggle />
          <button type="button" className="ghost small" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="shell-main" data-module={module?.slug}>
        {children}
      </main>
    </div>
  )
}
