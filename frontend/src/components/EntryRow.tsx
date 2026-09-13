import { Link } from 'react-router-dom'
import type { TrackedItem } from '../api/client'
import { detailPathFor } from '../modules/registry'
import { ChevronRight } from './GroupedList'
import { progressLine, progressShare } from './progress'
import { EntryCoverMenu } from './CoverStatusMenu'
import { StatusMenu } from './StatusMenu'

/**
 * One title as a row of the library's list view: cover, title, progress and a bar towards the
 * total where there is one.
 *
 * <p>The whole row opens the title, so the link is stretched over it and the status menu stands
 * above that. Wide, the menu is the labelled one beside the chevron, where a row has room to
 * say what shelf a title is on. On a phone ({@code compact}) the row keeps the native app's
 * shape of cover, text and chevron, so the menu is the round "…" on the cover's corner, the
 * same control in the same place as on a phone's grid card.
 */
export const EntryRow = ({
  entry,
  onChanged,
  onEdit,
  compact = false,
}: {
  entry: TrackedItem
  onChanged: (updated: TrackedItem) => void
  onEdit: () => void
  compact?: boolean
}) => {
  const progress = progressLine(entry)
  const share = progressShare(entry)
  const menu = compact ? (
    <EntryCoverMenu entry={entry} onChanged={onChanged} onOpenEditor={onEdit} />
  ) : (
    <StatusMenu entry={entry} onChanged={onChanged} onOpenEditor={onEdit} />
  )

  return (
    <li className="entry-row">
      <div className="entry-row-cover">
        {entry.coverUrl ? (
          <img src={entry.coverUrl} alt="" loading="lazy" />
        ) : (
          <div className="cover-placeholder" aria-hidden="true" />
        )}
        {compact && menu}
      </div>

      <Link className="entry-row-link" to={detailPathFor(entry)}>
        <span className="entry-row-title">{entry.title}</span>
        {progress && <span className="entry-row-progress">{progress}</span>}
        {share !== null && (
          <span className="entry-row-bar" aria-hidden="true">
            <span style={{ width: `${share * 100}%` }} />
          </span>
        )}
      </Link>

      {!compact && menu}
      <ChevronRight />
    </li>
  )
}
