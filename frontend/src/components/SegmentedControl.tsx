import type { ReactNode } from 'react'

export interface SegmentedOption<T extends string> {
  value: T
  label: ReactNode
}

/**
 * One choice out of a few, as buttons joined into a single control.
 *
 * <p>Pressed buttons in a group rather than a radio group: every segment is a stop for Tab, so
 * nothing has to be learnt about arrow keys to reach the one wanted, and each still says
 * whether it is the chosen one.
 */
export const SegmentedControl = <T extends string>({
  label,
  options,
  value,
  onChange,
  className,
}: {
  /** Names the control for a screen reader; nothing on screen shows it. */
  label: string
  options: SegmentedOption<T>[]
  value: T
  onChange: (value: T) => void
  className?: string
}) => (
  <div
    className={className ? `segmented ${className}` : 'segmented'}
    role="group"
    aria-label={label}
  >
    {options.map((option) => {
      const chosen = option.value === value
      return (
        <button
          key={option.value}
          type="button"
          className={chosen ? 'active' : 'ghost'}
          aria-pressed={chosen}
          onClick={() => onChange(option.value)}
        >
          {option.label}
        </button>
      )
    })}
  </div>
)
