import { useState } from 'react'
import type { AchievementCatalogueEntry, AchievementProgress } from '../api/client'
import { completionPercent } from './achievements'

/** How many unlocked achievements the collapsed list shows before "show all" is needed. */
const PREVIEW_COUNT = 6

/**
 * What you unlocked, and — only if you ask — what you have not.
 *
 * <p>Locked achievements are hidden by default because their names give the game away: a
 * list of everything you have yet to do is a list of what happens later. How much of the
 * list is shown and whether it includes locked ones are separate choices, since wanting to
 * see more of your own progress is not the same as wanting to be told the rest.
 *
 * <p>Progress is optional, because the same list is worth reading on a game nobody here
 * tracks. Without it every achievement is locked, so the panel opens as a count and a way
 * to ask for the rest — the spoiler rule above holds hardest where there is no progress to
 * weigh against it.
 */
export const AchievementList = ({
  catalogue,
  progress,
}: {
  catalogue: AchievementCatalogueEntry[]
  progress: AchievementProgress | null
}) => {
  const [expanded, setExpanded] = useState(false)
  const [showLocked, setShowLocked] = useState(false)

  if (catalogue.length === 0) {
    return null
  }

  const unlocked = new Set(progress?.unlocked ?? [])
  const percent = progress ? completionPercent(progress) : 0
  const visible = showLocked ? catalogue : catalogue.filter((a) => unlocked.has(a.id))
  const lockedCount = catalogue.length - unlocked.size
  // Collapsed is a preview: a handful of rows at full height, nothing to scroll. Expanding
  // is what turns it into the long, scrollable list.
  const shown = expanded ? visible : visible.slice(0, PREVIEW_COUNT)
  const hiddenCount = visible.length - shown.length

  return (
    <section className="status-section">
      <h2>Achievements</h2>

      <div className="card achievement-card">
        <div className="achievement-summary">
          {progress ? (
            <>
              <strong>
                {progress.unlocked.length} of {progress.total}
              </strong>
              <span className="muted">{percent}% complete</span>
            </>
          ) : (
            <>
              <strong>{catalogue.length} achievements</strong>
              <span className="muted">Add this to your list to track them</span>
            </>
          )}
        </div>

        {progress && (
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
        )}

        <div className="achievement-actions">
          {visible.length > PREVIEW_COUNT && (
            <button type="button" className="ghost small" onClick={() => setExpanded((v) => !v)}>
              {expanded ? `Show ${PREVIEW_COUNT}` : `Show all ${visible.length}`}
            </button>
          )}

          {lockedCount > 0 && (
            <button type="button" className="ghost small" onClick={() => setShowLocked((v) => !v)}>
              {showLocked ? 'Hide locked' : `Show ${lockedCount} locked`}
            </button>
          )}
        </div>

        <ul className={expanded ? 'achievement-list expanded' : 'achievement-list'}>
          {shown.map((achievement) => {
            const isUnlocked = unlocked.has(achievement.id)
            const at = progress?.unlockedAt[achievement.id]

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

        {!expanded && hiddenCount > 0 && <p className="muted">and {hiddenCount} more</p>}
      </div>
    </section>
  )
}
