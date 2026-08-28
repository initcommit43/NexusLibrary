import { useCallback, useEffect, useState } from 'react'
import { api, type MediaType } from '../api/client'
import { MODULES, mediaTypesOf, type ModuleDefinition, type ModuleSlug } from './registry'

export interface ModuleAvailability {
  modules: ModuleDefinition[]
  /** Modules the backend has an adapter for and the reader has not switched off. */
  isAvailable: (slug: ModuleSlug) => boolean
  /** Whether the backend can serve it at all, regardless of the reader's own switch. */
  isBuilt: (slug: ModuleSlug) => boolean
  /** Whether the reader has switched it on. A module they never touched is on. */
  isEnabled: (slug: ModuleSlug) => boolean
  setEnabled: (slug: ModuleSlug, enabled: boolean) => Promise<void>
  firstAvailable: ModuleDefinition
  loading: boolean
}

/**
 * The single place that knows how modules are discovered: the media types the backend has an
 * adapter for, less the ones this reader has switched off. Pages ask whether a module is
 * available and never learn which of the two answers made it so.
 */
export const useModules = (): ModuleAvailability => {
  const [mediaTypes, setMediaTypes] = useState<MediaType[] | null>(null)
  const [disabled, setDisabled] = useState<MediaType[]>([])

  useEffect(() => {
    api
      .availableModules()
      .then(setMediaTypes)
      // A failed lookup should not empty the sidebar; assume nothing and let pages report.
      .catch(() => setMediaTypes([]))

    api
      .disabledModules()
      .then(setDisabled)
      // Failing to read the switches leaves everything on, which is the safer wrong answer.
      .catch(() => setDisabled([]))
  }, [])

  const moduleFor = (slug: ModuleSlug) => MODULES.find((candidate) => candidate.slug === slug)

  const isBuilt = (slug: ModuleSlug) => {
    const module = moduleFor(slug)
    if (!module || mediaTypes === null) return false
    return mediaTypesOf(module).some((type) => mediaTypes.includes(type))
  }

  // Off only when every one of its media types is off, so a half-disabled module cannot exist.
  const isEnabled = (slug: ModuleSlug) => {
    const module = moduleFor(slug)
    if (!module) return false
    return !mediaTypesOf(module).every((type) => disabled.includes(type))
  }

  const isAvailable = (slug: ModuleSlug) => isBuilt(slug) && isEnabled(slug)

  const setEnabled = useCallback(
    async (slug: ModuleSlug, enabled: boolean) => {
      const module = moduleFor(slug)
      if (!module) return

      const types = mediaTypesOf(module)
      const next = enabled
        ? disabled.filter((type) => !types.includes(type))
        : [...new Set([...disabled, ...types])]

      // Shown as saved before the server says so: a checkbox that lags a round trip reads as
      // broken. A failure puts it back, which is the only case anyone sees a change undone.
      const previous = disabled
      setDisabled(next)
      try {
        setDisabled(await api.setDisabledModules(next))
      } catch {
        setDisabled(previous)
      }
    },
    [disabled],
  )

  return {
    modules: MODULES,
    isAvailable,
    isBuilt,
    isEnabled,
    setEnabled,
    firstAvailable: MODULES.find((module) => isAvailable(module.slug)) ?? MODULES[0],
    loading: mediaTypes === null,
  }
}
