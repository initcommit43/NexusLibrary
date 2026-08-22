import { RATING_STEPS } from './rating'

/** A 10-point scale with half steps, converted to the 0-100 the API stores. */
type Props = {
  value: number | null
  onChange: (rating: number) => void
  disabled?: boolean
  label: string
}

export const RatingInput = ({ value, onChange, disabled, label }: Props) => (
  <select
    className="status-picker"
    value={value ?? ''}
    disabled={disabled}
    aria-label={label}
    onChange={(e) => onChange(Number(e.target.value))}
  >
    <option value="" disabled>
      Rate…
    </option>
    {RATING_STEPS.map((score) => (
      <option key={score} value={score * 10}>
        {score.toFixed(1)}
      </option>
    ))}
  </select>
)
