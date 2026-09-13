import { useSyncExternalStore } from 'react'

/** The width the header folds at; index.css switches its narrow layout at the same one. */
const NARROW_SCREEN = '(max-width: 52rem)'

const subscribe = (onChange: () => void) => {
  const query = window.matchMedia(NARROW_SCREEN)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

const isNarrow = () => window.matchMedia(NARROW_SCREEN).matches

/**
 * Whether the screen is at the header's narrow width, following the window as it resizes.
 *
 * <p>For behaviour a stylesheet cannot switch: what a tap on a control does, rather than how
 * the control looks.
 */
export const useNarrowScreen = (): boolean => useSyncExternalStore(subscribe, isNarrow)
