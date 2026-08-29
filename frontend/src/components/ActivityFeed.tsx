import type { ActivityEntry } from '../api/client'
import { Tooltip } from './charts/Tooltip'
import { useTooltip } from './charts/useTooltip'
import { describe, isRun, relative, runTitle } from './activity'

/** How many of a run's titles the hover card names before it starts counting instead. */
const NAMED_IN_HOVER = 8

/**
 * What happened, newest first.
 *
 * <p>Two kinds of row share the list. A title's own event wears its cover; a run — an import,
 * a sync — belongs to no title, so it wears its provider's initial and says what it moved.
 *
 * <p>A run's titles are one hover away rather than one click: the row is a summary and the
 * detail is a glance, not a place to navigate to. Hover is not a gesture a touch screen or a
 * keyboard has, so the same card opens on tap and on focus.
 */
export const ActivityFeed = ({ feed }: { feed: ActivityEntry[] }) => {
  const tip = useTooltip()

  return (
    <>
      <ul className="activity-feed">
        {feed.map((activity) => {
          const run = isRun(activity)
          const titles = activity.payload.titles ?? []

          const show = (event: { clientX: number; clientY: number }) => {
            if (titles.length === 0) return

            const named = titles.slice(0, NAMED_IN_HOVER).map((change) => {
              const moved = change.from === null ? change.to : `${change.from} → ${change.to}`
              return `${change.title} · ${moved}`
            })
            const rest = titles.length - named.length

            tip.show(event, {
              title: runTitle(activity),
              lines: [...named, ...(rest > 0 ? [`and ${rest} more`] : [])],
            })
          }

          return (
            <li
              key={activity.id}
              className={run ? 'activity-row is-run' : 'activity-row'}
              tabIndex={run && titles.length > 0 ? 0 : undefined}
              onMouseMove={run ? show : undefined}
              onMouseLeave={run ? tip.hide : undefined}
              onFocus={
                run
                  ? (event) => {
                      const box = event.currentTarget.getBoundingClientRect()
                      show({ clientX: box.left + box.width / 2, clientY: box.top })
                    }
                  : undefined
              }
              onBlur={run ? tip.hide : undefined}
            >
              {run ? (
                <span className="activity-run-mark" aria-hidden="true">
                  {runTitle(activity).charAt(0)}
                </span>
              ) : activity.coverUrl ? (
                <img src={activity.coverUrl} alt="" loading="lazy" />
              ) : (
                <div className="activity-thumb-placeholder" aria-hidden="true" />
              )}

              <div className="activity-text">
                <strong>{run ? runTitle(activity) : activity.title}</strong>
                <span className="muted">{describe(activity)}</span>
              </div>

              <time className="muted" dateTime={activity.createdAt}>
                {relative(activity.createdAt)}
              </time>
            </li>
          )
        })}
      </ul>

      <Tooltip tip={tip.tip} />
    </>
  )
}
