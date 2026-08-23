import { NavLink } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { ModuleSwitcher } from './ModuleSwitcher'
import { ThemeToggle } from './ThemeToggle'
import type { ModuleDefinition } from '../modules/registry'

/**
 * Pages that belong to a module pass it in, which names it in the switcher; the pages that
 * span every module leave it out rather than claiming one you are not looking at.
 */
const CogIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <circle cx="12" cy="12" r="3.2" strokeWidth="1.8" />
    <path
      d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5v.2a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H2.9a2 2 0 1 1 0-4H3a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3H9a1.7 1.7 0 0 0 1-1.5V2.9a2 2 0 1 1 4 0V3a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9V9a1.7 1.7 0 0 0 1.5 1h.2a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1Z"
      strokeWidth="1.4"
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
)

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
        </nav>

        <div className="header-right">
          <span className="muted">{user?.username}</span>
          <ThemeToggle />
          <NavLink className="ghost icon-button" to="/settings" aria-label="Settings" title="Settings">
            <CogIcon />
          </NavLink>
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
