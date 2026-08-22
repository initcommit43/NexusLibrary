/**
 * Ratings are stored 0-100 because that is a superset of every source's scale, but the UI
 * speaks the 10-point half-step convention used by AniList and MAL.
 */
export const RATING_STEPS = Array.from({ length: 20 }, (_, i) => (i + 1) / 2)

export const toDisplayScore = (stored: number | null | undefined) =>
  stored === null || stored === undefined ? null : (stored / 10).toFixed(1)
