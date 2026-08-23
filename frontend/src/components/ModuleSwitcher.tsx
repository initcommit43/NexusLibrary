import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useModules } from '../modules/useModules'
import type { ModuleDefinition } from '../modules/registry'

/**
 * Names the module you are in, and switches to another. A menu rather than a list of links:
 * with four modules the rail would otherwise spend most of its height on navigation you use
 * once a session.
 */
export const ModuleSwitcher = ({ current }: { current: ModuleDefinition }) => {
  const { modules, isAvailable } = useModules()
  const [open, setOpen] = useState(false)
  const container = useRef<HTMLDivElement>(null)
  const navigate = useNavigate()

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
    navigate(`/library/${module.slug}`)
  }

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
          {modules.map((module) => {
            const available = isAvailable(module.slug)
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
