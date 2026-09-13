import { NavLink, useParams } from 'react-router-dom'
import { ModuleSwitcher } from './ModuleSwitcher'
import { AccountMenu } from './AccountMenu'
import { Footer } from './Footer'
import { HeaderSearch } from './HeaderSearch'
import { JobDock } from './JobDock'
import { OutageBanner } from './OutageBanner'
import { ThemeToggle } from './ThemeToggle'
import { useCurrentModule } from '../modules/useCurrentModule'
import { defaultTypeOf, typeBySlug } from '../modules/registry'
import { useHideOnScroll } from './useHideOnScroll'
import type { ModuleDefinition } from '../modules/registry'

/**
 * The links the header's nav and the phone's tab bar share. A module contributes its own
 * shelves; the rest is the same everywhere.
 */
const ShelfLinks = ({ module }: { module: ModuleDefinition }) => (
  <>
    {/* Home is the module's own page, so it takes the end prop its shelves do not. */}
    <NavLink to="/" end>
      Home
    </NavLink>
    {module.types.map((type) => (
      <NavLink key={type.slug} to={`/library/${module.slug}/${type.slug}`}>
        {type.label}
      </NavLink>
    ))}
    <NavLink to="/browse">Browse</NavLink>
  </>
)

/**
 * The module you are in stays put on every page, behind the mark that switches it: settings and
 * activity span modules, but you are still somewhere, and remembering the last module you
 * picked is what keeps the right shelves in the nav while you are there.
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
          <ModuleSwitcher current={current} />
        </div>

        <nav className="shell-nav" aria-label="Main">
          <ShelfLinks module={current} />
          {/* Not on the tab bar: at its width the account menu opens on a tap and leads with this. */}
          <NavLink to="/profile">Profile</NavLink>
        </nav>

        <div className="header-right">
          <HeaderSearch module={current} type={searchable} />
          <AccountMenu />
        </div>
      </header>

      {/*
        * The header's nav again for a phone; the stylesheet only ever displays one of the two, so
        * a screen reader meets a single Main. Outside the header because the transform that
        * slides it away would pin a fixed bar to the header instead of the window.
        */}
      <nav className="tab-bar" aria-label="Main">
        <ShelfLinks module={current} />
      </nav>

      {/* Above the page, not inside it: an outage is app-wide news, not one page's. */}
      <OutageBanner />

      <main className="shell-main" data-module={current.slug}>
        {children}
      </main>

      {/*
        * Between the page and the footer rather than after it, and sticky rather than fixed.
        * It still rides the bottom of the window while you read, because light or dark is a choice
        * about the page in front of you and has to be reachable from wherever that page has got
        * to. But where the page ends it stops above the footer's rule instead of landing on it.
        */}
      <div className="theme-dock">
        <ThemeToggle />
      </div>

      {/*
        * Sits outside the page, since a run outlives whichever page started it. Beside the theme
        * dock rather than after the footer, so it comes to rest above the same rule.
        */}
      <JobDock />

      {/* Below the page rather than inside it: what it links to is true of the whole app. */}
      <Footer />
    </div>
  )
}
