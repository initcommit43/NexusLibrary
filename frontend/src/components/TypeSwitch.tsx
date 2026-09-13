import type { MediaTypeDefinition, ModuleDefinition } from '../modules/registry'

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
    <div
      className={className ? `type-switch ${className}` : 'type-switch'}
      role="group"
      aria-label={`${module.label} type`}
    >
      {module.types.map((type) => (
        <button
          key={type.mediaType}
          type="button"
          className={type.mediaType === active.mediaType ? 'active' : 'ghost'}
          aria-pressed={type.mediaType === active.mediaType}
          onClick={() => onSwitch(type)}
        >
          {type.label}
        </button>
      ))}
    </div>
  )
}
