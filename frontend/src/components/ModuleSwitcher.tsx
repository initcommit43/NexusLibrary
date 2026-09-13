import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { api, type MediaType } from '../api/client'
import { rememberModule } from '../modules/useCurrentModule'
import { useModules } from '../modules/useModules'
import { defaultTypeOf, mediaTypesOf, type ModuleDefinition } from '../modules/registry'
import { ChevronRight } from './GroupedList'
import { useMenuDismiss } from './useMenuDismiss'

const trackedIn = (tracked: MediaType[], module: ModuleDefinition) => {
  const types = mediaTypesOf(module)
  return tracked.filter((mediaType) => types.includes(mediaType)).length
}

/**
 * The same page, in the module being switched to.
 *
 * <p>Switching module is switching what you are looking at, not where you are looking: a
 * reader on browse who switches to films wants that module's browse, and one on their profile
 * wants their profile. Only the pages whose address names a module have to be rebuilt; the
 * rest simply follow the choice, which is why nothing is returned for them.
 *
 * <p>Filters are dropped on the way. They are named per module — a genre AniList files under
 * "Slice of Life" is not one TMDB has — so carrying them over asks the new module for things
 * it has never heard of and answers with an empty page.
 */
const sameKindOfPage = (path: string, search: string, module: ModuleDefinition): string | null => {
  const type = defaultTypeOf(module)

  if (path.startsWith('/library/')) return `/library/${module.slug}/${type.slug}`

  // Both the browse page and a shelf of it, since a shelf id belongs to the module that
  // named it and means nothing in another.
  if (path.startsWith('/browse')) return `/browse?module=${module.slug}&type=${type.slug}`

  if (path.startsWith('/search')) {
    const params = new URLSearchParams(search)
    params.set('module', module.slug)
    params.delete('type')
    return `/search?${params}`
  }

  // A title's page belongs to the source that holds the title, so there is no such page in
  // another module: the module's own home is where switching from one lands.
  if (path.startsWith('/media/')) return '/'

  return null
}

/** The logo beside a name: the module you are in, or the app's own when there is no other. */
const Mark = ({ name }: { name: string }) => (
  <>
    <img src="/pwa-192x192.png" alt="" width={28} height={28} />
    <span>{name}</span>
  </>
)

const Chevron = () => (
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 9 6 6 6-6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

/**
 * The logo and the module you are in, which open the menu of modules. A menu rather than a
 * list of links: with four modules the header would otherwise spend most of its width on
 * navigation you use once a session.
 *
 * <p>Bare beside the logo rather than a pill of its own. The pill was the widest thing in the
 * bar and crowded the shelves out of it on a phone; joined to the logo, the name still says
 * what is selected and the arrow says how to change it.
 *
 * <p>A phone has no header, so there the same menu hangs from Home's title instead: the
 * {@code title} variant is that page's h1, with the name inside it as the trigger. The menu
 * sits beside the heading rather than in it, so its options are never read as the page's name.
 */
export const ModuleSwitcher = ({
  current,
  variant = 'brand',
}: {
  current: ModuleDefinition
  variant?: 'brand' | 'title'
}) => {
  const { modules, isBuilt, isEnabled } = useModules()
  const { open, setOpen, container, trigger } = useMenuDismiss<HTMLDivElement, HTMLButtonElement>()
  const navigate = useNavigate()
  const location = useLocation()

  const choose = (module: ModuleDefinition) => {
    setOpen(false)
    // Said before navigating, so a page with no module in its address redraws as this one.
    rememberModule(module.slug)

    const to = sameKindOfPage(location.pathname, location.search, module)
    if (to) navigate(to)
  }

  /*
   * A module switched off in settings is not listed at all; one that is simply not built yet
   * stays, since what is coming is part of what the app is.
   */
  const listed = modules.filter((module) => isEnabled(module.slug))

  const title = variant === 'title'

  // The same memoized read Home makes, so the counts cost no request of their own.
  const [tracked, setTracked] = useState<MediaType[] | null>(null)
  useEffect(() => {
    if (!title) return
    let live = true
    api
      .listEntries()
      .then((entries) => {
        if (live) setTracked(entries.map((entry) => entry.mediaType))
      })
      // No count is the answer to a failed read; the menu still switches.
      .catch(() => {})
    return () => {
      live = false
    }
  }, [title])

  // With one module there is nothing to switch between, so the logo is only the app's.
  if (listed.length <= 1) {
    return title ? (
      <h1 className="page-title">{current.label}</h1>
    ) : (
      <div className="brand">
        <Mark name="Nexus" />
      </div>
    )
  }

  const toggle = () => setOpen((wasOpen) => !wasOpen)

  return (
    <div className={title ? 'module-switcher title-switcher' : 'module-switcher'} ref={container}>
      {title ? (
        // Named by the module alone, so the heading it sits in reads as the page's name.
        <h1 className="page-title">
          <button
            ref={trigger}
            type="button"
            className="title-trigger"
            aria-haspopup="menu"
            aria-expanded={open}
            title="Switch module"
            onClick={toggle}
          >
            <span>{current.label}</span>
            <Chevron />
          </button>
        </h1>
      ) : (
        <button
          ref={trigger}
          type="button"
          className="brand brand-trigger"
          aria-haspopup="menu"
          aria-expanded={open}
          aria-label={`${current.label}, switch module`}
          title="Switch module"
          onClick={toggle}
        >
          <Mark name={current.label} />
          <Chevron />
        </button>
      )}

      {open && (
        <ul className={title ? 'module-menu group' : 'module-menu'} role="menu">
          {listed.map((module) => {
            const available = isBuilt(module.slug)
            const count = tracked ? trackedIn(tracked, module) : null
            // Blank rather than 0 while the count loads, and kept as a line so no row jumps.
            const subtitle =
              module.slug === current.slug
                ? 'Current'
                : !available
                  ? 'Not built yet'
                  : count === null
                    ? ' '
                    : `${count.toLocaleString()} tracked`
            return (
              <li key={module.slug} role="none">
                <button
                  type="button"
                  role="menuitem"
                  className={title ? 'module-option group-row is-two-line' : 'module-option'}
                  disabled={!available}
                  aria-current={module.slug === current.slug}
                  onClick={() => choose(module)}
                >
                  {title ? (
                    // The phone's picker, a grouped row saying how much of each module is yours.
                    <span className="group-row-body">
                      <span className="group-row-text">
                        <span className="group-row-title">{module.label}</span>
                        <span className="group-row-subtitle">{subtitle}</span>
                      </span>
                      <ChevronRight />
                    </span>
                  ) : (
                    <>
                      <span>{module.label}</span>
                      {!available && <span className="muted">Not built yet</span>}
                    </>
                  )}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
