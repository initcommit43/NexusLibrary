import type { MediaTypeDefinition, ModuleDefinition } from '../modules/registry'
import { SegmentedControl } from './SegmentedControl'

/**
 * A module's types as one segmented control, shared so every page that switches type shows
 * the same switch.
 *
 * <p>Nothing for a module with a single type: games would render a switch with one side.
 */
export const TypeSwitch = ({
  module,
  active,
  onSwitch,
  className,
}: {
  module: ModuleDefinition
  active: MediaTypeDefinition
  onSwitch: (type: MediaTypeDefinition) => void
  className?: string
}) => {
  if (module.types.length < 2) return null

  return (
    <SegmentedControl
      label={`${module.label} type`}
      options={module.types.map((type) => ({ value: type.mediaType, label: type.label }))}
      value={active.mediaType}
      onChange={(mediaType) => {
        const type = module.types.find((candidate) => candidate.mediaType === mediaType)
        if (type) onSwitch(type)
      }}
      className={className}
    />
  )
}
