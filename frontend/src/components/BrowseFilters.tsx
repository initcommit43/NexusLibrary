import { useEffect, useId, useRef, useState } from 'react'
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
  label,
  onClear,
  onToggle,
}: {
  filled: boolean
  label: string
  onClear: () => void
  /** Present for a control with a list behind it; the chevron opens it. */
  onToggle?: () => void
}) => {
  if (filled) {
    return (
      <button type="button" className="filter-x" aria-label={`Clear ${label}`} onClick={onClear}>
        <ClearIcon />
      </button>
    )
  }
  // Out of the tab order: the box itself opens the list from the keyboard.
  return onToggle ? (
    <button
      type="button"
      className="filter-chevron"
      tabIndex={-1}
      aria-label={`Show ${label} options`}
      onClick={onToggle}
    >
      <ChevronIcon />
    </button>
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
  const listId = useId()
  const [typed, setTyped] = useState('')
  const filled = chosen.length > 0

  // A search belongs to the one opening of the list: closed, the box shows what is chosen, and
  // reopened, the list starts whole again.
  const search = open ? typed : ''
  const reveal = () => {
    if (!open) setTyped('')
    setOpen(true)
  }

  /*
   * The box doubles as the search. AniList files under a few dozen genres and several hundred
   * tags, and scrolling a menu of three hundred words to find "found family" is not looking
   * for it; a second box inside the menu only repeated the one already on the bar.
   */
  const term = search.trim().toLowerCase()
  const offered = term
    ? field.options.filter((option) => option.label.toLowerCase().includes(term))
    : field.options

  /** What is chosen, in the words it was chosen by rather than the values it is sent as. */
  const labelled = chosen.map(
    (value) => field.options.find((option) => option.value === value)?.label ?? value,
  )

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
    <div
      className="filter"
      ref={container}
      data-float={filled ? '' : undefined}
      data-typing={search ? '' : undefined}
    >
      <label className="filter-label" htmlFor={id}>
        {field.label}
      </label>

      {/* What is chosen stands in the placeholder, so the first letter typed clears it away
          and the box holds only the search while there is one. */}
      <input
        id={id}
        className="filter-control"
        type="text"
        role="combobox"
        autoComplete="off"
        aria-expanded={open}
        aria-controls={listId}
        aria-autocomplete="list"
        value={search}
        placeholder={labelled.join(', ')}
        onClick={reveal}
        onChange={(event) => {
          reveal()
          setTyped(event.target.value)
        }}
        onKeyDown={(event) => {
          if (event.key === 'ArrowDown') reveal()
          // Enter takes the best match, so a typed name needs no reach for the mouse.
          if (event.key === 'Enter' && term && offered.length > 0) {
            event.preventDefault()
            pick(offered[0].value)
          }
        }}
      />

      <Affordance
        filled={filled}
        label={field.label}
        onClear={() => onChange([])}
        onToggle={() => (open ? setOpen(false) : reveal())}
      />

      {open && (
        <ul className="filter-options" id={listId} role="listbox" aria-multiselectable={multiple}>
          {/* Named here, where it is a choice among others, rather than in the closed box,
              where it would be a word standing in for the nothing it means. */}
          {!multiple && !term && (
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

          {offered.length === 0 && (
            <li className="filter-empty muted">Nothing by that name</li>
          )}

          {offered.map((option) =>
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

/** How long typing has to pause before the text is written back to the URL. */
const WRITE_BACK_MS = 150

/**
 * A free-text box. Its label rests where the value will be until the first letter is typed.
 *
 * <p>The text is held here rather than read straight from the URL. The URL catches up a beat
 * after each keystroke, and a box bound to it was put back to the older text in between, so a
 * quick typist lost letters. It writes back once typing pauses, and follows the URL only when
 * something else moves it — a step back, a reset — never when the URL is merely catching up.
 */
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
  const [text, setText] = useState(value)
  const [sent, setSent] = useState(value)
  const [seen, setSeen] = useState(value)

  if (value !== seen) {
    setSeen(value)
    if (value !== sent) {
      setText(value)
      setSent(value)
    }
  }

  const writing = useRef<ReturnType<typeof setTimeout>>(undefined)
  // A box left mid-pause on the way off the page has nothing left to write to.
  useEffect(() => () => clearTimeout(writing.current), [])

  const write = (next: string) => {
    setSent(next)
    onChange(next ? [next] : [])
  }

  const type = (next: string) => {
    setText(next)
    clearTimeout(writing.current)
    writing.current = setTimeout(() => write(next), WRITE_BACK_MS)
  }

  const clear = () => {
    clearTimeout(writing.current)
    setText('')
    write('')
  }

  return (
    <div className="filter" data-typing={text ? '' : undefined}>
      <label className="filter-label" htmlFor={id}>
        {field.label}
      </label>

      <input
        id={id}
        className="filter-control"
        type="search"
        value={text}
        onChange={(event) => type(event.target.value)}
      />

      <Affordance filled={text !== ''} label={field.label} onClear={clear} />
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
