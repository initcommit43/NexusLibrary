import type { MediaType, TrackingStatus } from '../api/client'
import { statusLabelsFor } from '../modules/registry'
import { STATUS_ORDER } from './trackingStatus'

type Props = {
  value: TrackingStatus
  /** The words follow the thing being tracked: you watch anime and read manga. */
  mediaType: MediaType
  onChange: (status: TrackingStatus) => void
  disabled?: boolean
  'aria-label'?: string
}

export const StatusPicker = ({ value, mediaType, onChange, disabled, ...rest }: Props) => {
  const labels = statusLabelsFor(mediaType)

  return (
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
}
