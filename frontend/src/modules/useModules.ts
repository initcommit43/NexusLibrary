import { useEffect, useState } from 'react'
import { api, type MediaType } from '../api/client'
import { MODULES, type ModuleDefinition, type ModuleSlug } from './registry'

export interface ModuleAvailability {
  modules: ModuleDefinition[]
  /** Modules the backend actually has an adapter for. The rest show as coming later. */
  isAvailable: (slug: ModuleSlug) => boolean
  firstAvailable: ModuleDefinition
  loading: boolean
}

/**
 * The single place that knows how modules are discovered. Today that is the media types the
 * backend reports an adapter for; when settings gain a per-user toggle it becomes this plus
 * the disabled list, and no page has to change.
 */
export const useModules = (): ModuleAvailability => {
  const [mediaTypes, setMediaTypes] = useState<MediaType[] | null>(null)

  useEffect(() => {
    api
      .availableModules()
      .then(setMediaTypes)
      // A failed lookup should not empty the sidebar; assume nothing and let pages report.
      .catch(() => setMediaTypes([]))
  }, [])

  const isAvailable = (slug: ModuleSlug) => {
    const module = MODULES.find((candidate) => candidate.slug === slug)
    if (!module || mediaTypes === null) return false
    return module.mediaTypes.some((type) => mediaTypes.includes(type))
  }

  return {
    modules: MODULES,
    isAvailable,
    firstAvailable: MODULES.find((module) => isAvailable(module.slug)) ?? MODULES[0],
    loading: mediaTypes === null,
  }
}
