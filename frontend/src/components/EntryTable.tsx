import { useState, type CSSProperties } from 'react'
import { Link } from 'react-router-dom'
import { api, type TrackedItem } from '../api/client'
import { mediaPathFor, type ListColumn } from '../modules/registry'
import { LIST_COLUMNS, STEPPED_UNITS } from './listColumns'
import { episodesWaiting } from './progress'
import { StatusMenu } from './StatusMenu'
import { coverBlurClass, useBlurAdult } from '../content/blur'

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
  const blur = useBlurAdult()
  const [stepping, setStepping] = useState(false)
  const [stepFailed, setStepFailed] = useState(false)

  const waiting = episodesWaiting(entry)
  const current = entry.progressCurrent ?? 0
  const steppable =
    entry.progressUnit !== null &&
    STEPPED_UNITS.has(entry.progressUnit) &&
    (entry.progressMax === null || current < entry.progressMax)

  const step = async () => {
    setStepping(true)
    setStepFailed(false)
    try {
      onChanged(await api.updateEntry(entry.id, { progressCurrent: current + 1 }))
    } catch {
      setStepFailed(true)
    } finally {
      setStepping(false)
    }
  }

  return (
    <div role="row" className="entry-table-row">
      <div role="cell" className="entry-table-thumb">
        {waiting !== null && (
          <span
            className="entry-table-airing"
            title={waiting > 0 ? `${waiting} waiting to watch` : 'Airing, and you are caught up'}
          />
        )}
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
        <StatusMenu entry={entry} onChanged={onChanged} onOpenEditor={onEdit} dots />
        {/* The thumbnail is too small to recognise a title by; hovering shows it at size. */}
        {entry.coverUrl && (
          <img
            className={coverBlurClass(entry.adult, blur, 'entry-table-preview')}
            src={entry.coverUrl}
            alt=""
            aria-hidden="true"
          />
        )}
      </div>

      <div role="cell" className="entry-table-title">
        <Link className="entry-table-link" to={mediaPathFor(entry)}>
          {entry.title}
        </Link>
      </div>

      {columns.map((column) => (
        <div role="cell" key={column} className="entry-table-value">
          <span className="entry-table-count">
            {LIST_COLUMNS[column].read(entry)}
            {column === 'progress' && steppable && (
              <button
                type="button"
                className={stepFailed ? 'entry-table-step is-failed' : 'entry-table-step'}
                aria-label={`${stepFailed ? 'Could not save. ' : ''}Add one to ${entry.title}`}
                title={stepFailed ? 'Could not save that. Try again.' : undefined}
                disabled={stepping}
                onClick={() => void step()}
              >
                +
              </button>
            )}
          </span>
        </div>
      ))}
    </div>
  )
}

/**
 * One status section of the library as a table, for a wide screen: a small cover, the title,
 * and the columns the media type asks for in the registry.
 *
 * <p>The row opens the title, so its link is stretched over the whole row, and the controls a
 * row carries stand above that link. They stay out of sight until the row is hovered or
 * focused, because every row has them and a column of forty "…" buttons is a wall.
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
