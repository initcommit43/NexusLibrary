import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useMenuDismiss } from './useMenuDismiss'

const UserIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <circle cx="12" cy="8" r="3.6" strokeWidth="1.8" />
    <path
      d="M4.5 20a7.5 7.5 0 0 1 15 0"
      strokeWidth="1.8"
      strokeLinecap="round"
    />
  </svg>
)

/**
 * Everything to do with the account, behind one icon.
 *
 * <p>The icon is the way to your own profile, because that is what someone reaching for their
 * own face is nearly always after; the rest of the account opens around it on the way past.
 *
 * <p>Opens on hover, and on focus as well: hover is not a gesture a keyboard has, and with the
 * icon now leading somewhere there is no click left to open it with. Focus travelling into the
 * menu keeps it open, and leaving the whole thing closes it.
 */
export const AccountMenu = () => {
  const { logout } = useAuth()
  const { open, setOpen, container } = useMenuDismiss<HTMLDivElement>()

  return (
    <div
      className="account"
      ref={container}
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      // Only when focus has left the menu entirely, not while it moves between the items.
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
      }}
    >
      <Link
        className="icon-button"
        to="/profile"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Your profile"
        title="Your profile"
        onClick={() => setOpen(false)}
      >
        <UserIcon />
      </Link>

      {open && (
        <div className="account-menu">
          <ul role="menu">
            <li role="none">
              <Link role="menuitem" to="/profile" onClick={() => setOpen(false)}>
                Profile
              </Link>
            </li>
            <li role="none">
              <Link role="menuitem" to="/stats" onClick={() => setOpen(false)}>
                Stats
              </Link>
            </li>
            <li role="none">
              <Link role="menuitem" to="/settings" onClick={() => setOpen(false)}>
                Settings
              </Link>
            </li>
            <li role="none">
              <button
                type="button"
                role="menuitem"
                className="danger"
                onClick={() => void logout()}
              >
                Sign out
              </button>
            </li>
          </ul>
        </div>
      )}
    </div>
  )
}
