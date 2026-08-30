/**
 * How a tracked change is said out loud.
 *
 * <p>Lifted out of the activity page when the home page grew a feed of its own: two pages
 * describing the same event in two voices is how "Watched episode 7" and "Progress 6 → 7"
 * end up on one screen.
 */
import type { ActivityEntry, TrackingStatus } from '../api/client'
import { MODULES, statusLabelsFor, type ModuleDefinition } from '../modules/registry'
import { toDisplayScore } from './rating'

const MINUTES_PER_HOUR = 60

/** Whether this happened to a run rather than to one title. */
export const isRun = (activity: ActivityEntry): boolean =>
  activity.type === 'IMPORTED' || activity.type === 'SYNCED'

/**
 * Which module a row belongs to, for a feed that can be narrowed to one.
 *
 * <p>A title's own event says so by its medium; a run says so by the provider it ran against,
 * which is the same answer by the other route — Steam is the games module however you arrive.
 */
export const moduleOf = (activity: ActivityEntry): ModuleDefinition | undefined =>
  activity.mediaType
    ? MODULES.find((module) => module.types.some((type) => type.mediaType === activity.mediaType))
    : MODULES.find((module) =>
        module.providers.some((provider) => provider.provider === activity.payload.provider),
      )

/** What a provider is called, falling back to its own name where no module claims it. */
const providerLabel = (activity: ActivityEntry): string => {
  const provider = activity.payload.provider
  const named = MODULES.flatMap((module) => module.providers).find(
    (candidate) => candidate.provider === provider,
  )
  return named?.label ?? provider ?? 'a provider'
}

const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`

/** Reads the stored old-to-new payload back as a sentence, in the module's own words. */
export const describe = (activity: ActivityEntry): string => {
  if (isRun(activity)) {
    const { added = 0, advanced = 0 } = activity.payload
    const brought = added > 0 ? `${plural(added, 'title', 'titles')} in` : null
    const moved = advanced > 0 ? `${plural(advanced, 'title', 'titles')} on` : null

    return [brought, moved].filter(Boolean).join(', ')
  }

  /*
   * An imported event is said in the words the provider said it in — "watched episode 5",
   * "read chapter 12", "completed". Mapping those onto this app's own vocabulary would lose
   * what they actually record: a chapter read on a Tuesday is not a status change.
   */
  if (activity.type === 'EXTERNAL') {
    const { status, progress } = activity.payload
    const said = [status, progress].filter(Boolean).join(' ')
    return said.charAt(0).toUpperCase() + said.slice(1)
  }

  const labels = statusLabelsFor(activity.mediaType ?? 'GAME')
  const statusLabel = (raw?: string | null) =>
    raw && raw in labels ? labels[raw as TrackingStatus] : raw
  const { from, to, unit } = activity.payload

  switch (activity.type) {
    case 'ADDED':
      return `Added to ${statusLabel(activity.payload.status) ?? 'your library'}`
    case 'STATUS_CHANGE':
      return from ? `${statusLabel(from)} → ${statusLabel(to)}` : `Moved to ${statusLabel(to)}`
    case 'RATED':
      // Stored 0-100, shown on the 10-point scale the rating input uses.
      return `Rated ${toDisplayScore(Number(to))}`
    case 'PROGRESS':
      return unit === 'MINUTES'
        ? `Played ${(Number(to) / MINUTES_PER_HOUR).toFixed(1)} hours`
        : `Progress ${from ?? 0} → ${to}`
    case 'REVIEWED':
      return 'Wrote a review'
    default:
      return ''
  }
}

/** What the row calls itself where there is no title to name. */
export const runTitle = (activity: ActivityEntry): string =>
  activity.type === 'IMPORTED'
    ? `Imported from ${providerLabel(activity)}`
    : `${providerLabel(activity)} sync`

export const relative = (iso: string) => {
  const minutes = Math.round((Date.now() - new Date(iso).getTime()) / 60000)
  if (minutes < 1) return 'just now'
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.round(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  return new Date(iso).toLocaleDateString()
}
