/**
 * An on/off switch, the native app's, over a real checkbox: the checkbox carries the state,
 * the focus and the name, and the track beside it is only how it is drawn at a phone's width.
 */
export const Toggle = ({
  label,
  checked,
  onChange,
  disabled,
}: {
  /** What is switched, for a screen reader; the row the switch sits in shows it on screen. */
  label: string
  checked: boolean
  onChange: (checked: boolean) => void
  disabled?: boolean
}) => (
  <span className="toggle">
    <input
      type="checkbox"
      role="switch"
      aria-label={label}
      checked={checked}
      disabled={disabled}
      onChange={(event) => onChange(event.target.checked)}
    />
    <span className="toggle-track" aria-hidden />
  </span>
)
