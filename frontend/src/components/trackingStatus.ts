import type { TrackingStatus } from '../api/client'

export const STATUS_LABELS: Record<TrackingStatus, string> = {
  PLANNING: 'Backlog',
  IN_PROGRESS: 'Playing',
  COMPLETED: 'Completed',
  PAUSED: 'Paused',
  DROPPED: 'Dropped',
}

/** Dashboard section order: what you are doing now first, what you abandoned last. */
export const STATUS_ORDER: TrackingStatus[] = [
  'IN_PROGRESS',
  'PLANNING',
  'COMPLETED',
  'PAUSED',
  'DROPPED',
]
