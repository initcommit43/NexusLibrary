import { useEffect, useState } from 'react'
import { applyTheme, storeTheme, storedTheme, systemTheme, type Theme } from '../theme'

export const ThemeToggle = () => {
  const [theme, setTheme] = useState<Theme>(() => storedTheme() ?? systemTheme())

  // Until someone picks a side the palette follows the OS, so the icon has to follow it too
  // — otherwise switching Windows to dark leaves a sun sitting on a dark page.
  useEffect(() => {
    if (storedTheme()) {
      return
    }
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const follow = () => setTheme(systemTheme())
    media.addEventListener('change', follow)
    return () => media.removeEventListener('change', follow)
  }, [theme])

  const toggle = () => {
    const next: Theme = theme === 'dark' ? 'light' : 'dark'
    setTheme(next)
    storeTheme(next)
    applyTheme(next)
  }

  const goingDark = theme === 'light'

  return (
    <button
      type="button"
      className="ghost icon-button"
      onClick={toggle}
      aria-label={goingDark ? 'Switch to dark mode' : 'Switch to light mode'}
      title={goingDark ? 'Switch to dark mode' : 'Switch to light mode'}
    >
      {goingDark ? <MoonIcon /> : <SunIcon />}
    </button>
  )
}

const MoonIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <path
      d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8Z"
      strokeWidth="1.8"
      strokeLinejoin="round"
    />
  </svg>
)

const SunIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <circle cx="12" cy="12" r="4" strokeWidth="1.8" />
    <path
      d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4 1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
      strokeWidth="1.8"
      strokeLinecap="round"
    />
  </svg>
)
