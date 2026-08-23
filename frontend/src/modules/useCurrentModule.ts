import { useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { moduleBySlug, type ModuleDefinition } from './registry'
import { useModules } from './useModules'

const STORAGE_KEY = 'nexus-module'

const remembered = (): ModuleDefinition | undefined => {
  try {
    return moduleBySlug(localStorage.getItem(STORAGE_KEY) ?? undefined)
  } catch {
    // Private windows and blocked site data throw on access; falling back is fine.
    return undefined
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
  const fromRoute = explicit ?? moduleBySlug(slug)

  useEffect(() => {
    if (!fromRoute) return
    try {
      localStorage.setItem(STORAGE_KEY, fromRoute.slug)
    } catch {
      // The choice still holds for this tab; it just will not survive a reload.
    }
  }, [fromRoute])

  return fromRoute ?? remembered() ?? firstAvailable
}
