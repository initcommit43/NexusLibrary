import { Link } from 'react-router-dom'
import type { TrackedItem } from '../api/client'
import { mediaPathFor } from '../modules/registry'
import { ChevronRight } from './GroupedList'
import { progressLine, progressShare } from './progress'
import { EntryCoverMenu } from './CoverStatusMenu'
import { coverBlurClass, useBlurAdult } from '../content/blur'

/**
 * One title as a row of the library's list view on a phone: cover, title, progress and a bar
 * towards the total where there is one. A wide screen gets {@code EntryTable} instead.
 *
 * <p>The whole row opens the title, so the link is stretched over it and the status menu stands
 * above that. The row keeps the native app's shape of cover, text and chevron, so the menu is
 * the round "…" on the cover's corner, the same control in the same place as on a phone's grid
 * card.
 */
export const EntryRow = ({
  entry,
  onChanged,
  onEdit,
}: {
  entry: TrackedItem
  onChanged: (updated: TrackedItem) => void
  onEdit: () => void
}) => {
  const blur = useBlurAdult()
  const progress = progressLine(entry)
  const share = progressShare(entry)

  return (
    <li className="entry-row">
      <div className="entry-row-cover">
        {entry.coverUrl ? (
          <img
            className={coverBlurClass(entry.adult, blur)}
            src={entry.coverUrl}
            alt=""
            loading="lazy"
          />
        ) : (
          <div className="cover-placeholder" aria-hidden="true" />
        )}
        <EntryCoverMenu entry={entry} onChanged={onChanged} onOpenEditor={onEdit} />
      </div>

      <Link className="entry-row-link" to={mediaPathFor(entry)}>
        <span className="entry-row-title">{entry.title}</span>
        {progress && <span className="entry-row-progress">{progress}</span>}
        {share !== null && (
          <span className="entry-row-bar" aria-hidden="true">
            <span style={{ width: `${share * 100}%` }} />
          </span>
        )}
      </Link>

      <ChevronRight />
    </li>
  )
}
