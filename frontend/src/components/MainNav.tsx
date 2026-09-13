import { Link, NavLink, useMatch } from 'react-router-dom'
import { defaultTypeOf, type MediaTypeDefinition, type ModuleDefinition } from '../modules/registry'

/** A place the main navigation leads, whichever of its two forms is showing it. */
interface Destination {
  to: string
  label: string
  /** Only the address itself counts as being there, rather than anything under it too. */
  end?: boolean
}

/*
 * Home is the module's own page, so it takes the end prop the others do not: every address
 * sits under it.
 */
const HOME: Destination = { to: '/', label: 'Home', end: true }
const BROWSE: Destination = { to: '/browse', label: 'Browse' }
const PROFILE: Destination = { to: '/profile', label: 'Profile' }

const shelfOf = (module: ModuleDefinition, type: MediaTypeDefinition): Destination => ({
  to: `/library/${module.slug}/${type.slug}`,
  label: type.label,
})

/**
 * The header's links: a module contributes a link per shelf, since a wide screen has the room
 * to name each of them. The rest is the same everywhere.
 */
export const HeaderLinks = ({ module }: { module: ModuleDefinition }) => (
  <>
    {[HOME, ...module.types.map((type) => shelfOf(module, type)), BROWSE, PROFILE].map((place) => (
      <NavLink key={place.to} to={place.to} end={place.end}>
        {place.label}
      </NavLink>
    ))}
  </>
)

const Icon = ({ children }: { children: React.ReactNode }) => (
  <svg
    viewBox="0 0 24 24"
    width="22"
    height="22"
    fill="none"
    stroke="currentColor"
    strokeWidth="1.8"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden
  >
    {children}
  </svg>
)

const HomeIcon = () => (
  <Icon>
    <path d="M4 10.5 12 4l8 6.5V19a1 1 0 0 1-1 1h-4.5v-5.5h-5V20H5a1 1 0 0 1-1-1Z" />
  </Icon>
)

const LibraryIcon = () => (
  <Icon>
    <path d="M12 6.5C10.3 5.2 7.8 4.5 4 4.5V18c3.8 0 6.3.7 8 2 1.7-1.3 4.2-2 8-2V4.5c-3.8 0-6.3.7-8 2Z" />
    <path d="M12 6.5V20" />
  </Icon>
)

const BrowseIcon = () => (
  <Icon>
    <circle cx="12" cy="12" r="8.5" />
    <path d="m15.5 8.5-2.2 4.8-4.8 2.2 2.2-4.8Z" />
  </Icon>
)

const ProfileIcon = () => (
  <Icon>
    <circle cx="12" cy="8" r="3.6" />
    <path d="M4.5 20a7.5 7.5 0 0 1 15 0" />
  </Icon>
)

/**
 * One tab, marked as the current page for anything at or under {@code within}.
 *
 * <p>A plain link rather than a NavLink, because Library leads to one shelf and is still
 * where you are on any other: NavLink only knows the address it leads to.
 */
const Tab = ({
  place,
  within = place.to,
  icon,
}: {
  place: Destination
  within?: string
  icon: React.ReactNode
}) => {
  const here = useMatch({ path: within, end: place.end ?? false }) !== null

  return (
    <Link
      to={place.to}
      className={here ? 'active' : undefined}
      aria-current={here ? 'page' : undefined}
    >
      {icon}
      <span>{place.label}</span>
    </Link>
  )
}

/**
 * The phone's tabs. One for the library rather than one per shelf: four tabs is what a thumb
 * can tell apart at this width, and the library page switches between its own shelves. It
 * opens on the module's first shelf, the same one its bare path would.
 */
export const TabLinks = ({ module }: { module: ModuleDefinition }) => (
  <>
    <Tab place={HOME} icon={<HomeIcon />} />
    <Tab
      place={{ ...shelfOf(module, defaultTypeOf(module)), label: 'Library' }}
      within="/library"
      icon={<LibraryIcon />}
    />
    <Tab place={BROWSE} icon={<BrowseIcon />} />
    <Tab place={PROFILE} icon={<ProfileIcon />} />
  </>
)
