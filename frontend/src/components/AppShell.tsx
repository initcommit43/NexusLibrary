import { NavLink, useParams } from 'react-router-dom'
import { ModuleSwitcher } from './ModuleSwitcher'
import { AccountMenu } from './AccountMenu'
import { HeaderSearch } from './HeaderSearch'
import { ImportIndicator } from './ImportIndicator'
import { OutageBanner } from './OutageBanner'
import { ThemeToggle } from './ThemeToggle'
import { useCurrentModule } from '../modules/useCurrentModule'
import { defaultTypeOf, typeBySlug } from '../modules/registry'
import { useHideOnScroll } from './useHideOnScroll'
import type { ModuleDefinition } from '../modules/registry'

/**
 * The switcher names the module you are in and stays put on every page: settings and
 * activity span modules, but you are still somewhere, and remembering the last module you
 * picked is what keeps the right shelves in the header while you are there.
 */
export const AppShell = ({
  children,
  module,
}: {
  children: React.ReactNode
  module?: ModuleDefinition
}) => {
  const current = useCurrentModule(module)
  const hidden = useHideOnScroll()

  // Which shelf search will cover. Pages that span modules carry no type, so they get the
  // module's first — the same shelf its bare path opens.
  const searchable = typeBySlug(current, useParams().type) ?? defaultTypeOf(current)

  return (
    <div className="shell">
      <header className={hidden ? 'shell-header hidden' : 'shell-header'}>
        <div className="header-left">
          <div className="brand">
            <img src="/pwa-192x192.png" alt="" width={28} height={28} />
            <span>Nexus</span>
          </div>

          <ModuleSwitcher current={current} />
        </div>

        {/* A module contributes its own shelves; the rest of the header is the same everywhere. */}
        <nav className="shell-nav">
          {current.types.map((type) => (
            <NavLink key={type.slug} to={`/library/${current.slug}/${type.slug}`}>
              {type.listLabel}
            </NavLink>
          ))}
          <NavLink to="/browse">Browse</NavLink>
          <NavLink to="/profile">Profile</NavLink>
        </nav>

        <div className="header-right">
          <HeaderSearch module={current} type={searchable} />
          <ThemeToggle />
          <AccountMenu />
        </div>
      </header>

      {/* Above the page, not inside it: an outage is app-wide news, not one page's. */}
      <OutageBanner />

      <main className="shell-main" data-module={current.slug}>
        {children}
      </main>

      {/* Sits outside the page, since a run outlives whichever page started it. */}
      <ImportIndicator />
    </div>
  )
}
