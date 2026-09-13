/** Which of the two lists is showing: what you did, or what happened to what you keep. */
export type FeedKind = 'activity' | 'notifications'

export const FEED_LABELS: Record<FeedKind, string> = {
  activity: 'Activity',
  notifications: 'Notifications',
}

/** The page each list has of its own. */
export const FEED_PATHS: Record<FeedKind, string> = {
  activity: '/activity',
  notifications: '/notifications',
}
