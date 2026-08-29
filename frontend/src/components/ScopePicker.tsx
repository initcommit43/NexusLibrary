import { useEffect, useRef, useState } from 'react'
import type { ModuleDefinition } from '../modules/registry'

/**
 * Which module a panel is showing, and a way to change it.
 *
 * <p>The same control as the switcher in the header, doing something else with it: that one
 * moves you to a module, this one points a panel at one while you stay where you are. The
 * shape is shared deliberately — a reader who has met one has met both.
 */
export const ScopePicker = ({
  modules,
  current,
  onChoose,
}: {
  modules: ModuleDefinition[]
  current: ModuleDefinition
  onChoose: (module: ModuleDefinition) => void
}) => {
  const [open, setOpen] = useState(false)
  const container = useRef<HTMLDivElement>(null)

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

  if (modules.length <= 1) return null

  return (
    <div className="module-switcher scope-picker" ref={container}>
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
          {modules.map((module) => (
            <li key={module.slug} role="none">
              <button
                type="button"
                role="menuitem"
                className="module-option"
                aria-current={module.slug === current.slug}
                onClick={() => {
                  setOpen(false)
                  onChoose(module)
                }}
              >
                <span>{module.label}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
