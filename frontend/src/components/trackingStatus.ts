import type { TrackingStatus } from '../api/client'

/**
 * Section order: what you are doing now first, what you abandoned last. The words for each
 * status belong to the media type, and live in the module registry.
 */
export const STATUS_ORDER: TrackingStatus[] = [
  'IN_PROGRESS',
  'PLANNING',
  'COMPLETED',
  'PAUSED',
  'DROPPED',
]
