import { useId } from 'react'
import type { FilterField, FilterValues } from '../api/client'
import { useMenuDismiss } from './useMenuDismiss'

const ChevronIcon = () => (
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 9 6 6 6-6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

/**
 * A control that takes any number of its options.
 *
 * <p>Not a native multiple select: that renders as a scrolling list box which takes as much
 * height as the rest of the bar and needs a modifier key to add a second value, neither of
 * which a reader picking two genres expects.
 */
const MultiSelect = ({
  field,
  chosen,
  onChange,
}: {
  field: FilterField
  chosen: string[]
  onChange: (next: string[]) => void
}) => {
  const { open, setOpen, container } = useMenuDismiss<HTMLDivElement>()
  const labelId = useId()

  const toggle = (value: string) =>
    onChange(chosen.includes(value) ? chosen.filter((held) => held !== value) : [...chosen, value])

  return (
    <div className="filter filter-multi" ref={container} data-float="">
      <span className="filter-label" id={labelId}>
        {field.label}
      </span>

      <button
        type="button"
        className="filter-control"
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-labelledby={labelId}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <span>{chosen.length === 0 ? 'Any' : chosen.join(', ')}</span>
        <ChevronIcon />
      </button>

      {open && (
        <ul className="filter-options">
          {field.options.map((option) => (
            <li key={option.value}>
              <label>
                <input
                  type="checkbox"
                  checked={chosen.includes(option.value)}
                  onChange={() => toggle(option.value)}
                />
                <span>{option.label}</span>
              </label>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * The bar above a browse page, built from whatever the module's adapter said it can answer.
 *
 * <p>This file knows nothing about seasons or genres: it renders text boxes, selects and
 * multi-selects from a list, and hands back the values by field id. A module gains a filter
 * by declaring one in its adapter, the way it gains a shelf.
 *
 * <p>Labels sit inside their control rather than above it. A select always shows something,
 * so its label is floated from the start; a text box has nothing to show until it is typed
 * in, so its label rests in the middle and does the work of a placeholder until then.
 */
export const BrowseFilters = ({
  fields,
  values,
  onChange,
  onClear,
}: {
  fields: FilterField[]
  values: FilterValues
  onChange: (next: FilterValues) => void
  onClear: () => void
}) => {
  const set = (field: string, next: string[]) => onChange({ ...values, [field]: next })
  const chosenIn = (field: string) => values[field] ?? []
  const narrowed = fields.some((field) => chosenIn(field.id).some(Boolean))

  return (
    <div className="filter-bar">
      {fields.map((field) => {
        const chosen = chosenIn(field.id)

        if (field.kind === 'MULTI') {
          return (
            <MultiSelect
              key={field.id}
              field={field}
              chosen={chosen}
              onChange={(next) => set(field.id, next)}
            />
          )
        }

        return (
          <label className="filter" key={field.id} data-float={field.kind === 'TEXT' ? undefined : ''}>
            <span className="filter-label">{field.label}</span>

            {field.kind === 'TEXT' ? (
              <input
                className="filter-control"
                type="search"
                value={chosen[0] ?? ''}
                onChange={(event) => set(field.id, event.target.value ? [event.target.value] : [])}
              />
            ) : (
              <select
                className="filter-control"
                value={chosen[0] ?? ''}
                onChange={(event) => set(field.id, event.target.value ? [event.target.value] : [])}
              >
                <option value="">Any</option>
                {field.options.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            )}
          </label>
        )
      })}

      {/* Only once there is something to clear: an always-on reset is a permanent dead button. */}
      {narrowed && (
        <button type="button" className="ghost small filter-clear" onClick={onClear}>
          Clear
        </button>
      )}
    </div>
  )
}
