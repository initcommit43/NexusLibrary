import type { FilterField, FilterValues } from '../api/client'
import { useMenuDismiss } from './useMenuDismiss'

export type BrowseView = 'grid' | 'list'

const TagIcon = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden>
    <path d="M3 4.5A1.5 1.5 0 0 1 4.5 3h6.4a1.5 1.5 0 0 1 1.06.44l8.1 8.1a1.5 1.5 0 0 1 0 2.12l-6.4 6.4a1.5 1.5 0 0 1-2.12 0l-8.1-8.1A1.5 1.5 0 0 1 3 10.9V4.5zM7.5 9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z" />
  </svg>
)

const SortIcon = () => (
  <svg viewBox="0 0 24 24" width="12" height="12" fill="currentColor" aria-hidden>
    <path d="M12 3l5 6H7zM12 21l-5-6h10z" />
  </svg>
)

const GridIcon = () => (
  <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden>
    <path d="M3 3h8v8H3zM13 3h8v8h-8zM3 13h8v8H3zM13 13h8v8h-8z" />
  </svg>
)

const ListIcon = () => (
  <svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden>
    <path d="M3 4h4v4H3zM9 4h12v4H9zM3 10h4v4H3zM9 10h12v4H9zM3 16h4v4H3zM9 16h12v4H9z" />
  </svg>
)

const RemoveIcon = () => (
  <svg viewBox="0 0 24 24" width="11" height="11" fill="none" stroke="currentColor" aria-hidden>
    <path d="m6 6 12 12M18 6 6 18" strokeWidth="3" strokeLinecap="round" />
  </svg>
)

/** One value the grid is narrowed by, in the words its control showed it. */
type Applied = { field: string; value: string; label: string }

const appliedIn = (fields: FilterField[], values: FilterValues): Applied[] =>
  fields
    .filter((field) => field.kind !== 'SORT')
    .flatMap((field) =>
      (values[field.id] ?? []).filter(Boolean).map((value) => ({
        field: field.id,
        value,
        label:
          field.kind === 'TEXT'
            ? `“${value}”`
            : (field.options.find((option) => option.value === value)?.label ?? value),
      })),
    )

/** The order the grid is in: named by what it is, chosen from a short menu beside it. */
const SortMenu = ({
  field,
  chosen,
  onChange,
}: {
  field: FilterField
  chosen: string | undefined
  onChange: (value: string) => void
}) => {
  const { open, setOpen, container, trigger } = useMenuDismiss<HTMLDivElement, HTMLButtonElement>()
  const current =
    field.options.find((option) => option.value === (chosen ?? field.defaultValue)) ?? field.options[0]

  return (
    <div className="sort-menu" ref={container}>
      <button
        ref={trigger}
        type="button"
        className="sort-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={`Sort by ${current?.label ?? ''}`}
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <SortIcon />
        {current?.label}
      </button>

      {open && (
        <ul className="sort-options" role="menu">
          {field.options.map((option) => (
            <li key={option.value} role="none">
              <button
                type="button"
                role="menuitemradio"
                aria-checked={option.value === current?.value}
                className={option.value === current?.value ? 'chosen' : undefined}
                onClick={() => {
                  setOpen(false)
                  onChange(option.value)
                }}
              >
                {option.label}
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

/**
 * The row between the filter bar and the results: what the results are narrowed by, on the
 * left, and how they are ordered and laid out, on the right.
 *
 * <p>Each narrowing is a chip that takes itself off when its cross is pressed — the quick way
 * out of one filter without going back up to the control that set it. The two layouts are
 * bare icons, as AniList's are: they change how the page is drawn, not what is on it, and a
 * boxed control would give them the weight of a filter.
 */
export const BrowseToolbar = ({
  fields,
  values,
  onChange,
  view,
  onView,
}: {
  fields: FilterField[]
  values: FilterValues
  onChange: (next: FilterValues) => void
  view: BrowseView
  onView: (view: BrowseView) => void
}) => {
  const applied = appliedIn(fields, values)
  const sort = fields.find((field) => field.kind === 'SORT')

  const remove = ({ field, value }: Applied) =>
    onChange({ ...values, [field]: (values[field] ?? []).filter((held) => held !== value) })

  return (
    <div className="browse-toolbar">
      {applied.length > 0 && (
        <div className="applied-filters">
          <span className="applied-mark" aria-hidden="true">
            <TagIcon />
          </span>
          <ul aria-label="Applied filters">
            {applied.map((chip) => (
              <li key={`${chip.field}:${chip.value}`}>
                <button
                  type="button"
                  className="applied-chip"
                  aria-label={`Remove ${chip.label}`}
                  title="Remove"
                  onClick={() => remove(chip)}
                >
                  {chip.label}
                  <span className="applied-x" aria-hidden="true">
                    <RemoveIcon />
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      )}

      <div className="browse-arrange">
        {sort && sort.options.length > 0 && (
          <SortMenu
            field={sort}
            chosen={values[sort.id]?.[0]}
            onChange={(value) => onChange({ ...values, [sort.id]: [value] })}
          />
        )}

        <div className="view-switch" role="group" aria-label="Layout">
          <button
            type="button"
            className={view === 'grid' ? 'view-option on' : 'view-option'}
            aria-pressed={view === 'grid'}
            aria-label="Covers"
            title="Covers"
            onClick={() => onView('grid')}
          >
            <GridIcon />
          </button>
          <button
            type="button"
            className={view === 'list' ? 'view-option on' : 'view-option'}
            aria-pressed={view === 'list'}
            aria-label="List"
            title="List"
            onClick={() => onView('list')}
          >
            <ListIcon />
          </button>
        </div>
      </div>
    </div>
  )
}
