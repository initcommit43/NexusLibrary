import { useParams } from 'react-router-dom'
import { ModuleSwitcher } from './ModuleSwitcher'
import { AccountMenu } from './AccountMenu'
import { Footer } from './Footer'
import { HeaderSearch } from './HeaderSearch'
import { JobDock } from './JobDock'
import { HeaderLinks, TabLinks } from './MainNav'
import { OutageBanner } from './OutageBanner'
import { ThemeToggle } from './ThemeToggle'
import { useCurrentModule } from '../modules/useCurrentModule'
import { defaultTypeOf, typeBySlug } from '../modules/registry'
import { useHideOnScroll } from './useHideOnScroll'
import { useScrolledPast } from './useScrolledPast'
import { useNarrowScreen } from './useNarrowScreen'
import type { ModuleDefinition } from '../modules/registry'

/**
 * The module you are in stays put on every page, behind the mark that switches it: settings and
 * activity span modules, but you are still somewhere, and remembering the last module you
 * picked is what keeps the right shelves in the nav while you are there.
 */
/** How far down a page's banner reaches, past which a see-through header turns solid again. */
const BANNER_REACH = 300

export const AppShell = ({
  children,
  module,
  overBanner = false,
}: {
  children: React.ReactNode
  module?: ModuleDefinition
  /**
   * The page runs a banner up behind the header, which then lets it show through while the
   * banner is what lies beneath it. A desk alone: a phone has no header to see through.
   */
  overBanner?: boolean
}) => {
  const current = useCurrentModule(module)
  const hidden = useHideOnScroll()
  const narrow = useNarrowScreen()
  const pastBanner = useScrolledPast(overBanner && !narrow ? BANNER_REACH : null)
  const seeThrough = overBanner && !narrow && !pastBanner

  // Which shelf search will cover. Pages that span modules carry no type, so they get the
  // module's first — the same shelf its bare path opens.
  const searchable = typeBySlug(current, useParams().type) ?? defaultTypeOf(current)

  return (
    <div className="shell">
      <header
        className={['shell-header', hidden && 'hidden', seeThrough && 'see-through']
          .filter(Boolean)
          .join(' ')}
      >
        <div className="header-left">
          <ModuleSwitcher current={current} />
        </div>

        <nav className="shell-nav" aria-label="Main">
          <HeaderLinks module={current} />
        </nav>

        <div className="header-right">
          {/*
            * Mounted here or on the tab bar, never both, so there is one trigger to reach and
            * one overlay that can be open.
            */}
          {!narrow && <HeaderSearch module={current} type={searchable} />}
          <AccountMenu />
        </div>
      </header>

      {/*
        * The way round on a phone, where the stylesheet hides the header and its Main with it.
        * Outside the header because the transform that slides it away would pin a fixed bar to
        * the header instead of the window. Search sits beside the tabs rather than among them:
        * it opens over the page you are on instead of leading to another.
        */}
      {narrow && (
        <div className="tab-bar">
          <nav className="tab-bar-tabs" aria-label="Main">
            <TabLinks module={current} />
          </nav>
          <HeaderSearch module={current} type={searchable} />
        </div>
      )}

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
        * A phone keeps it in the footer instead, where it covers nothing.
        */}
      {!narrow && (
        <div className="theme-dock">
          <ThemeToggle />
        </div>
      )}

      {/*
        * Sits outside the page, since a run outlives whichever page started it. Beside the theme
        * dock rather than after the footer, so it comes to rest above the same rule.
        */}
      <JobDock />

      {/* Below the page rather than inside it: what it links to is true of the whole app. */}
      <Footer aside={narrow && <ThemeToggle />} />
    </div>
  )
}
