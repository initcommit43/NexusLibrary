import { useState } from 'react'
import type { TrackedItem } from '../api/client'
import { achievementCatalogue, achievementProgress, completionPercent } from './achievements'

/** Unlocked first, so the list opens on what you actually did rather than what you missed. */
export const AchievementList = ({ entry }: { entry: TrackedItem }) => {
  const [showLocked, setShowLocked] = useState(false)

  const progress = achievementProgress(entry)
  const catalogue = achievementCatalogue(entry)

  if (!progress || catalogue.length === 0) {
    return null
  }

  const unlocked = new Set(progress.unlocked)
  const percent = completionPercent(progress)
  const shown = showLocked ? catalogue : catalogue.filter((a) => unlocked.has(a.id))

  return (
    <section className="status-section">
      <h2>Achievements</h2>

      <div className="card achievement-card">
        <div className="achievement-summary">
          <strong>
            {progress.unlocked.length} of {progress.total}
          </strong>
          <span className="muted">{percent}% complete</span>
        </div>

        <div
          className="achievement-bar"
          role="progressbar"
          aria-valuenow={percent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label="Achievement completion"
        >
          <div className="achievement-bar-fill" style={{ width: `${percent}%` }} />
        </div>

        <button type="button" className="ghost small" onClick={() => setShowLocked((v) => !v)}>
          {showLocked ? 'Show unlocked only' : `Show all ${progress.total}`}
        </button>

        <ul className="achievement-list">
          {shown.map((achievement) => {
            const isUnlocked = unlocked.has(achievement.id)
            const at = progress.unlockedAt[achievement.id]

            return (
              <li key={achievement.id} className={isUnlocked ? 'unlocked' : 'locked'}>
                {/* Steam ships a separate greyed-out icon for locked achievements. */}
                {(isUnlocked ? achievement.icon : achievement.lockedIcon) && (
                  <img
                    className="achievement-icon"
                    src={(isUnlocked ? achievement.icon : achievement.lockedIcon) ?? undefined}
                    alt=""
                    loading="lazy"
                  />
                )}
                <div className="achievement-text">
                  <strong>{achievement.name ?? achievement.id}</strong>
                  {/* A hidden achievement's description is a spoiler until it is earned. */}
                  {achievement.hidden && !isUnlocked ? (
                    <span className="muted">Hidden achievement</span>
                  ) : (
                    achievement.description && <span className="muted">{achievement.description}</span>
                  )}
                </div>
                {isUnlocked && at && (
                  // Steam reports unlock times in seconds, not milliseconds.
                  <time className="muted" dateTime={new Date(at * 1000).toISOString()}>
                    {new Date(at * 1000).toLocaleDateString()}
                  </time>
                )}
              </li>
            )
          })}
        </ul>
      </div>
    </section>
  )
}
