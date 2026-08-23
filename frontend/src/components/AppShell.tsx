import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ModuleSwitcher } from './ModuleSwitcher'
import { ThemeToggle } from './ThemeToggle'
import type { ModuleDefinition } from '../modules/registry'

/**
 * The rail names the module you are in and carries the pages that span all of them. Pages
 * that belong to a module pass it in; the cross-module ones leave it out and the switcher
 * stays hidden rather than claiming a module you are not looking at.
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
      <aside className="shell-rail">
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

        <div className="rail-footer">
          <span className="muted">{user?.username}</span>
          <div className="rail-actions">
            <ThemeToggle />
            <button type="button" className="ghost small" onClick={() => void logout()}>
              Sign out
            </button>
          </div>
        </div>
      </aside>

      <main className="shell-main" data-module={module?.slug}>
        {children}
      </main>
    </div>
  )
}
