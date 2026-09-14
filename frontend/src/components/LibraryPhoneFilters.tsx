import { useCallback, useEffect, useId, useRef, useState } from 'react'
import type { TrackedItem } from '../api/client'
import type { MediaTypeDefinition } from '../modules/registry'
import { ListFilterFields } from './ListSidebar'
import { EMPTY_FILTERS, activeFilterCount, countIn, type ListFilters } from './listFilters'
import { SearchField } from './SearchField'
import { useEscapeKey } from './useEscapeKey'

const FilterIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <path d="M4 7h16M7 12h10M10 17h4" strokeWidth="2" strokeLinecap="round" />
  </svg>
)

/**
 * The library's sidebar, rearranged for a phone: the lists as chips, the title search always
 * in view, and the rest of the filters in a sheet behind one button.
 *
 * <p>It edits the same filter state the sidebar does, so turning a phone sideways past the
 * breakpoint carries every choice across to the sidebar and back.
 */
export const LibraryPhoneFilters = ({
  type,
  entries,
  filters,
  onChange,
}: {
  type: MediaTypeDefinition
  entries: TrackedItem[]
  filters: ListFilters
  onChange: (next: ListFilters) => void
}) => {
  const [open, setOpen] = useState(false)
  // Stable, because the sheet moves focus when this changes and the parent redraws on every key.
  const close = useCallback(() => setOpen(false), [])
  const active = activeFilterCount(filters)

  return (
    <>
      <div className="chip-row" role="group" aria-label="Lists">
        {(['ALL', ...type.statusOrder] as const).map((status) => (
          <button
            key={status}
            type="button"
            className="chip"
            aria-pressed={filters.status === status}
            onClick={() => onChange({ ...filters, status })}
          >
            {status === 'ALL' ? 'All' : type.statusLabels[status]}
            <span className="chip-count">{countIn(entries, status)}</span>
          </button>
        ))}
      </div>

      <div className="library-search-row">
        <SearchField
          label="Search your library by title"
          placeholder="Search your library"
          value={filters.query}
          onChange={(e) => onChange({ ...filters, query: e.target.value })}
        />
        <button
          type="button"
          className="library-filter-button"
          aria-haspopup="dialog"
          aria-expanded={open}
          aria-label={active > 0 ? `Filters, ${active} active` : 'Filters'}
          onClick={() => setOpen(true)}
        >
          <FilterIcon />
          Filters
          {active > 0 && <span className="library-filter-count">{active}</span>}
        </button>
      </div>

      {open && (
        <FilterSheet
          type={type}
          entries={entries}
          filters={filters}
          onChange={onChange}
          onClose={close}
        />
      )}
    </>
  )
}

const FilterSheet = ({
  type,
  entries,
  filters,
  onChange,
  onClose,
}: {
  type: MediaTypeDefinition
  entries: TrackedItem[]
  filters: ListFilters
  onChange: (next: ListFilters) => void
  onClose: () => void
}) => {
  const titleId = useId()
  const panel = useRef<HTMLDivElement>(null)

  useEscapeKey(onClose)

  // Focus goes in so a screen reader lands in the sheet, and back to the button on the way out.
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    panel.current?.focus()

    return () => opener?.focus({ preventScroll: true })
    // Mount and unmount only: refocusing the sheet on any later render would take the cursor
    // out of whatever field the reader is typing in.
  }, [])

  return (
    <div className="dialog-backdrop" onClick={onClose} role="presentation">
      <div
        ref={panel}
        className="dialog dialog-confirm library-filter-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        onClick={(event) => event.stopPropagation()}
      >
        <header className="dialog-head">
          <h2 id={titleId}>Filters</h2>
        </header>

        <ListFilterFields type={type} entries={entries} filters={filters} onChange={onChange} />

        {/* The query and the chosen list stay: they have their own controls outside the sheet. */}
        <div className="dialog-foot">
          <button
            type="button"
            className="ghost"
            disabled={activeFilterCount(filters) === 0}
            onClick={() =>
              onChange({ ...EMPTY_FILTERS, query: filters.query, status: filters.status })
            }
          >
            Reset
          </button>
          <button type="button" onClick={onClose}>
            Done
          </button>
        </div>
      </div>
    </div>
  )
}
