import { useEffect, useSyncExternalStore } from 'react'
import { useParams } from 'react-router-dom'
import { moduleBySlug, type ModuleDefinition } from './registry'
import { useModules } from './useModules'

const STORAGE_KEY = 'nexus-module'

const read = (): string | null => {
  try {
    return localStorage.getItem(STORAGE_KEY)
  } catch {
    // Private windows and blocked site data throw on access; falling back is fine.
    return null
  }
}

/*
 * The chosen module is held here as well as in storage, and anyone reading it is told when it
 * changes. Pages that span the modules — the profile, the feed, home — have no module in
 * their address, so switching module on one of them changes nothing about the page and
 * everything about what it shows: without a subscription, nothing would redraw.
 */
let chosen: string | null = read()
const listeners = new Set<() => void>()

export const rememberModule = (slug: string) => {
  if (chosen === slug) return

  chosen = slug
  try {
    localStorage.setItem(STORAGE_KEY, slug)
  } catch {
    // The choice still holds for this tab; it just will not survive a reload.
  }
  listeners.forEach((notify) => notify())
}

const subscribe = (notify: () => void) => {
  listeners.add(notify)
  return () => {
    listeners.delete(notify)
  }
}

/**
 * Which module the app is currently showing.
 *
 * <p>The route decides when it says so; otherwise the last module you looked at does. Without
 * that memory, every page that spans modules — settings, activity — would send you back to
 * whichever module happens to be first, which is not where you were.
 */
export const useCurrentModule = (explicit?: ModuleDefinition): ModuleDefinition => {
  const { module: slug } = useParams()
  const { firstAvailable } = useModules()
  const remembered = useSyncExternalStore(subscribe, () => chosen)
  const fromRoute = explicit ?? moduleBySlug(slug)

  useEffect(() => {
    if (fromRoute) rememberModule(fromRoute.slug)
  }, [fromRoute])

  return fromRoute ?? moduleBySlug(remembered ?? undefined) ?? firstAvailable
}
