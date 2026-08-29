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
 * <p>Opens on hover so it costs nothing to look at, and on click as well — hover is not a
 * gesture a touch screen has, and clicking is what a keyboard's Enter arrives as.
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
    >
      <button
        type="button"
        className="ghost icon-button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Account"
        title="Account"
        onClick={() => setOpen((wasOpen) => !wasOpen)}
      >
        <UserIcon />
      </button>

      {open && (
        <div className="account-menu">
          <ul role="menu">
            <li role="none">
              <Link role="menuitem" to="/profile" onClick={() => setOpen(false)}>
                Profile
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
