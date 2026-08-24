import { useSyncExternalStore } from 'react'

/**
 * Which external services are currently known to be down.
 *
 * <p>Fed from the one place that sees every failure — the API client — whenever a response
 * or a background job names an unavailable service, so one banner can say "AniList is
 * down" instead of each feature failing on its own terms.
 *
 * <p>An entry expires a few minutes after the last sighting rather than on some later
 * success, because requests do not say which service they depend on: the honest claim is
 * "this service failed recently", and when the reports stop, so does the claim.
 */

const OUTAGE_TTL_MS = 3 * 60_000

type Outage = { service: string; at: number }

let outages: Outage[] = []
const listeners = new Set<() => void>()
const expiryTimers = new Map<string, ReturnType<typeof setTimeout>>()

const notify = () => listeners.forEach((listener) => listener())

const remove = (service: string) => {
  expiryTimers.delete(service)
  if (!outages.some((outage) => outage.service === service)) return
  outages = outages.filter((outage) => outage.service !== service)
  notify()
}

/** Called by the API client; a repeat sighting refreshes the clock rather than stacking. */
export const reportOutage = (service: string) => {
  const existing = expiryTimers.get(service)
  if (existing) clearTimeout(existing)
  expiryTimers.set(service, setTimeout(() => remove(service), OUTAGE_TTL_MS))

  if (!outages.some((outage) => outage.service === service)) {
    outages = [...outages, { service, at: Date.now() }]
    notify()
  }
}

/** Dismissal lasts until the next sighting: a fresh failure is fresh news. */
export const dismissOutage = (service: string) => {
  const timer = expiryTimers.get(service)
  if (timer) clearTimeout(timer)
  remove(service)
}

const subscribe = (listener: () => void) => {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

export const useOutages = (): readonly Outage[] =>
  useSyncExternalStore(subscribe, () => outages)
