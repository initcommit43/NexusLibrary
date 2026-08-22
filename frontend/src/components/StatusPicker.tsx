import type { TrackingStatus } from '../api/client'
import { STATUS_LABELS, STATUS_ORDER } from './trackingStatus'

type Props = {
  value: TrackingStatus
  onChange: (status: TrackingStatus) => void
  disabled?: boolean
  'aria-label'?: string
}

export const StatusPicker = ({ value, onChange, disabled, ...rest }: Props) => (
  <select
    className="status-picker"
    value={value}
    disabled={disabled}
    aria-label={rest['aria-label'] ?? 'Status'}
    onChange={(e) => onChange(e.target.value as TrackingStatus)}
  >
    {STATUS_ORDER.map((status) => (
      <option key={status} value={status}>
        {STATUS_LABELS[status]}
      </option>
    ))}
  </select>
)
