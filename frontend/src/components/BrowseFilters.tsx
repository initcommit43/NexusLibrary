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
 * A list of options, taking one value or several.
 *
 * <p>Written rather than left to a native select for two reasons. A native select shows its
 * chosen option's text when closed, so the only way to have an "Any" entry in the list is to
 * have the word sitting in the box before anyone has chosen anything — and a native multiple
 * select renders as a list box the height of the whole bar that needs a modifier key to add
 * a second value. Both are answered by owning the menu.
 */
const Dropdown = ({
  field,
  chosen,
  multiple,
  onChange,
}: {
  field: FilterField
  chosen: string[]
  multiple: boolean
  onChange: (next: string[]) => void
}) => {
  const { open, setOpen, container } = useMenuDismiss<HTMLDivElement>()
  const id = useId()
  const filled = chosen.length > 0

  const pick = (value: string) => {
    if (!multiple) {
      onChange([value])
      setOpen(false)
      return
    }
    // A second genre is a further question, not a different one, so the list stays open.
    onChange(chosen.includes(value) ? chosen.filter((held) => held !== value) : [...chosen, value])
  }

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
          {/* Named here, where it is a choice among others, rather than in the closed box,
              where it would be a word standing in for the nothing it means. */}
          {!multiple && (
            <li>
              <button
                type="button"
                className={filled ? undefined : 'chosen'}
                onClick={() => {
                  onChange([])
                  setOpen(false)
                }}
              >
                Any
              </button>
            </li>
          )}

          {field.options.map((option) =>
            multiple ? (
              <li key={option.value}>
                <label>
                  <input
                    type="checkbox"
                    checked={chosen.includes(option.value)}
                    onChange={() => pick(option.value)}
                  />
                  <span>{option.label}</span>
                </label>
              </li>
            ) : (
              <li key={option.value}>
                <button
                  type="button"
                  className={chosen[0] === option.value ? 'chosen' : undefined}
                  onClick={() => pick(option.value)}
                >
                  {option.label}
                </button>
              </li>
            ),
          )}
        </ul>
      )}
    </div>
  )
}

/** A free-text box. Its label rests where the value will be until there is one. */
const TextFilter = ({
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

  return (
    <div className="filter" data-float={value ? '' : undefined}>
      <label className="filter-label" htmlFor={id}>
        {field.label}
      </label>

      <input
        id={id}
        className="filter-control"
        type="search"
        value={value}
        onChange={(event) => onChange(event.target.value ? [event.target.value] : [])}
      />

      <Affordance
        filled={value !== ''}
        hasList={false}
        label={field.label}
        onClear={() => onChange([])}
      />
    </div>
  )
}

/**
 * The bar above a browse page, built from whatever the module's adapter said it can answer.
 *
 * <p>This file knows nothing about seasons or genres: it renders text boxes and option lists
 * from a list of fields, and hands back the values by field id. A module gains a filter by
 * declaring one in its adapter, the way it gains a shelf.
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
        const change = (next: string[]) => set(field.id, next)

        return field.kind === 'TEXT' ? (
          <TextFilter key={field.id} field={field} chosen={chosen} onChange={change} />
        ) : (
          <Dropdown
            key={field.id}
            field={field}
            chosen={chosen}
            multiple={field.kind === 'MULTI'}
            onChange={change}
          />
        )
      })}
    </div>
  )
}
