export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'nexus-theme'

/** What the OS asks for, which is what an unset preference follows. */
export const systemTheme = (): Theme =>
  window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'

export const storedTheme = (): Theme | null => {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : null
  } catch {
    // Private windows and blocked site data both throw on access. Following the OS is a
    // perfectly good answer, so a failed read is not worth surfacing.
    return null
  }
}

export const storeTheme = (theme: Theme) => {
  try {
    localStorage.setItem(STORAGE_KEY, theme)
  } catch {
    // The choice still applies to this tab; it just will not survive a reload.
  }
}

/**
 * Applies a preference, or clears it back to following the system.
 *
 * Only an explicit choice writes `data-theme`. Left unset, the palette follows
 * `prefers-color-scheme` on its own, so the app tracks the OS until someone opts out.
 */
export const applyTheme = (theme: Theme | null) => {
  const root = document.documentElement

  if (theme) {
    root.dataset.theme = theme
  } else {
    delete root.dataset.theme
  }

  syncBrowserChrome()
}

/**
 * Keeps the address bar and PWA title bar on the same colour as the page.
 *
 * Read from the resolved token rather than repeated as a literal here — this file would
 * otherwise be the second place a palette colour lives.
 */
const syncBrowserChrome = () => {
  const meta = document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')
  if (!meta) {
    return
  }
  const background = getComputedStyle(document.documentElement).getPropertyValue('--bg').trim()
  if (background) {
    meta.content = background
  }
}
