import type { AchievementCatalogueEntry, AchievementProgress, TrackedItem } from '../api/client'

/**
 * Achievements live in the two JSONB escape hatches rather than their own tables: the
 * catalogue on the shared item, because it is the same for every player, and the unlocks
 * on the user's entry. Reading them back means a little unwrapping, kept in one place.
 */
const ACHIEVEMENTS_KEY = 'achievements'

export const achievementCatalogue = (entry: TrackedItem): AchievementCatalogueEntry[] => {
  const raw = entry.metadata?.[ACHIEVEMENTS_KEY]
  return Array.isArray(raw) ? (raw as AchievementCatalogueEntry[]) : []
}

export const achievementProgress = (entry: TrackedItem): AchievementProgress | null => {
  const raw = entry.progressExtra?.[ACHIEVEMENTS_KEY]
  if (!raw || typeof raw !== 'object') return null

  const progress = raw as Partial<AchievementProgress>
  return {
    unlocked: Array.isArray(progress.unlocked) ? progress.unlocked : [],
    unlockedAt: progress.unlockedAt ?? {},
    total: typeof progress.total === 'number' ? progress.total : 0,
  }
}

export const completionPercent = (progress: AchievementProgress) =>
  progress.total === 0 ? 0 : Math.round((progress.unlocked.length / progress.total) * 100)
