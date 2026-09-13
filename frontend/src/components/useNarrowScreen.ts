import { useSyncExternalStore } from 'react'

/** The phone's width; index.css swaps the header for the tab bar at the same one. */
const NARROW_SCREEN = '(max-width: 52rem)'

const subscribe = (onChange: () => void) => {
  const query = window.matchMedia(NARROW_SCREEN)
  query.addEventListener('change', onChange)
  return () => query.removeEventListener('change', onChange)
}

const isNarrow = () => window.matchMedia(NARROW_SCREEN).matches

/**
 * Whether the screen is at the phone's width, following the window as it resizes.
 *
 * <p>For what a stylesheet cannot switch: how much a list loads, or which of two places a
 * control is mounted in, rather than how the control looks.
 */
export const useNarrowScreen = (): boolean => useSyncExternalStore(subscribe, isNarrow)
