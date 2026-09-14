import type { CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import type { TrackedItem } from '../api/client'
import { mediaPathFor, type ListColumn } from '../modules/registry'
import { LIST_COLUMNS } from './listColumns'
import { StatusMenu } from './StatusMenu'

const Row = ({
  entry,
  columns,
  onChanged,
  onEdit,
}: {
  entry: TrackedItem
  columns: ListColumn[]
  onChanged: (updated: TrackedItem) => void
  onEdit: () => void
}) => {
  return (
    <div role="row" className="entry-table-row">
      <div role="cell" className="entry-table-thumb">
        {entry.coverUrl ? (
          <img src={entry.coverUrl} alt="" loading="lazy" />
        ) : (
          <div className="cover-placeholder" aria-hidden="true" />
        )}
        <StatusMenu entry={entry} onChanged={onChanged} onOpenEditor={onEdit} dots />
      </div>

      <div role="cell" className="entry-table-title">
        <Link className="entry-table-link" to={mediaPathFor(entry)}>
          {entry.title}
        </Link>
      </div>

      {columns.map((column) => (
        <div role="cell" key={column} className="entry-table-value">
          {LIST_COLUMNS[column].read(entry)}
        </div>
      ))}
    </div>
  )
}

/**
 * One status section of the library as a table, for a wide screen: a small cover, the title,
 * and the columns the media type asks for in the registry.
 *
 * <p>The row opens the title, so its link is stretched over the whole row, and the status menu
 * stands above that link. It stays out of sight until the row is hovered or focused, because
 * every row has one and a column of forty "…" buttons is a wall.
 */
export const EntryTable = ({
  entries,
  columns,
  onChanged,
  onEdit,
}: {
  entries: TrackedItem[]
  columns: ListColumn[]
  onChanged: (updated: TrackedItem) => void
  onEdit: (entry: TrackedItem) => void
}) => (
  <div
    role="table"
    className="entry-table"
    style={{ '--entry-table-columns': columns.length } as CSSProperties}
  >
    <div role="row" className="entry-table-head">
      <span role="columnheader" aria-label="Cover" />
      <span role="columnheader">Title</span>
      {columns.map((column) => (
        <span role="columnheader" key={column}>
          {LIST_COLUMNS[column].label}
        </span>
      ))}
    </div>

    {entries.map((entry) => (
      <Row
        key={entry.id}
        entry={entry}
        columns={columns}
        onChanged={onChanged}
        onEdit={() => onEdit(entry)}
      />
    ))}
  </div>
)
