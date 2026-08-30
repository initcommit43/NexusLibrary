import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { rememberModule } from '../modules/useCurrentModule'
import { useModules } from '../modules/useModules'
import { defaultTypeOf, type ModuleDefinition } from '../modules/registry'

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

/**
 * Names the module you are in, and switches to another. A menu rather than a list of links:
 * with four modules the rail would otherwise spend most of its height on navigation you use
 * once a session.
 */
export const ModuleSwitcher = ({ current }: { current: ModuleDefinition }) => {
  const { modules, isBuilt, isEnabled } = useModules()
  const [open, setOpen] = useState(false)
  const container = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()
  const location = useLocation()

  useEffect(() => {
    if (!open) return

    const onPointerDown = (event: PointerEvent) => {
      if (!container.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }

    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
    }
  }, [open])

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

  // With one module there is nothing to switch between, and a control that only ever names
  // what you are already looking at is furniture. The shelves in the nav say which it is.
  if (listed.length <= 1) return null

  return (
    <div className="module-switcher" ref={container}>
      <button
        type="button"
        className="module-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <span>{current.label}</span>
        <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" aria-hidden>
          <path d="m6 9 6 6 6-6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
        </svg>
      </button>

      {open && (
        <ul className="module-menu" role="menu">
          {listed.map((module) => {
            const available = isBuilt(module.slug)
            return (
              <li key={module.slug} role="none">
                <button
                  type="button"
                  role="menuitem"
                  className="module-option"
                  disabled={!available}
                  aria-current={module.slug === current.slug}
                  onClick={() => choose(module)}
                >
                  <span>{module.label}</span>
                  {!available && <span className="muted">Not built yet</span>}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
