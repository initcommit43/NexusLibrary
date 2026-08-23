import type { TrackingStatus } from '../api/client'
import { STATUS_LABELS, STATUS_ORDER } from './trackingStatus'

type Props = {
  value: TrackingStatus
  onChange: (status: TrackingStatus) => void
  /** A module's own verbs — Playing, Watching, Reading. */
  labels?: Record<TrackingStatus, string>
  disabled?: boolean
  'aria-label'?: string
}

export const StatusPicker = ({ value, onChange, labels = STATUS_LABELS, disabled, ...rest }: Props) => (
  <select
    className="status-picker"
    value={value}
    disabled={disabled}
    aria-label={rest['aria-label'] ?? 'Status'}
    onChange={(e) => onChange(e.target.value as TrackingStatus)}
  >
    {STATUS_ORDER.map((status) => (
      <option key={status} value={status}>
        {labels[status]}
      </option>
    ))}
  </select>
)
