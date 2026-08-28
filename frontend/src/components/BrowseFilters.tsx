import { useId } from 'react'
import type { FilterField, FilterValues } from '../api/client'
import { useMenuDismiss } from './useMenuDismiss'

const ChevronIcon = () => (
  <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 9 6 6 6-6" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

const ClearIcon = () => (
  <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 6 12 12M18 6 6 18" strokeWidth="2" strokeLinecap="round" />
  </svg>
)

/**
 * The control's right-hand slot: what it does next.
 *
 * <p>Empty, that is the chevron saying there is a list behind this. Filled, it becomes the
 * way back out — undoing one control where a bar-wide reset would undo the lot, and sitting
 * inside the box rather than beside it, so nothing on the row moves when it appears.
 */
const Affordance = ({
  filled,
  hasList,
  label,
  onClear,
}: {
  filled: boolean
  hasList: boolean
  label: string
  onClear: () => void
}) => {
  if (filled) {
    return (
      <button type="button" className="filter-x" aria-label={`Clear ${label}`} onClick={onClear}>
        <ClearIcon />
      </button>
    )
  }
  return hasList ? (
    <span className="filter-chevron" aria-hidden>
      <ChevronIcon />
    </span>
  ) : null
}

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
  const id = useId()
  const filled = chosen.length > 0

  const toggle = (value: string) =>
    onChange(chosen.includes(value) ? chosen.filter((held) => held !== value) : [...chosen, value])

  return (
    <div className="filter" ref={container} data-float={filled ? '' : undefined}>
      <label className="filter-label" htmlFor={id}>
        {field.label}
      </label>

      <button
        id={id}
        type="button"
        className="filter-control"
        aria-haspopup="listbox"
        aria-expanded={open}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <span>{chosen.join(', ')}</span>
      </button>

      <Affordance filled={filled} hasList label={field.label} onClear={() => onChange([])} />

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
 * <p>Labels sit inside their control and float up once it holds something. An unset control
 * shows only its label — no word for "unset", because an empty Year filtering nothing is
 * what anyone would already assume.
 */
export const BrowseFilters = ({
  fields,
  values,
  onChange,
}: {
  fields: FilterField[]
  values: FilterValues
  onChange: (next: FilterValues) => void
}) => {
  const set = (field: string, next: string[]) => onChange({ ...values, [field]: next })

  return (
    <div className="filter-bar">
      {fields.map((field) => {
        const chosen = values[field.id] ?? []

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
          <Single
            key={field.id}
            field={field}
            chosen={chosen}
            onChange={(next) => set(field.id, next)}
          />
        )
      })}
    </div>
  )
}

/** A text box or a select: one value, or none. */
const Single = ({
  field,
  chosen,
  onChange,
}: {
  field: FilterField
  chosen: string[]
  onChange: (next: string[]) => void
}) => {
  const id = useId()
  const value = chosen[0] ?? ''
  const filled = value !== ''

  return (
    <div className="filter" data-float={filled ? '' : undefined}>
      <label className="filter-label" htmlFor={id}>
        {field.label}
      </label>

      {field.kind === 'TEXT' ? (
        <input
          id={id}
          className="filter-control"
          type="search"
          value={value}
          onChange={(event) => onChange(event.target.value ? [event.target.value] : [])}
        />
      ) : (
        <select
          id={id}
          className="filter-control"
          value={value}
          onChange={(event) => onChange(event.target.value ? [event.target.value] : [])}
        >
          {/* Unlabelled on purpose: unset should read as empty, not as a value called "Any". */}
          <option value="" />
          {field.options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      )}

      <Affordance
        filled={filled}
        hasList={field.kind !== 'TEXT'}
        label={field.label}
        onClear={() => onChange([])}
      />
    </div>
  )
}
