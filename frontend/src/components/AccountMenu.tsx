import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { useMenuDismiss } from './useMenuDismiss'
import { useNarrowScreen } from './useNarrowScreen'

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
 *
 * <p>On a narrow screen a tap on the icon opens the menu instead. A phone has no hover, so the
 * icon leading to the profile would leave settings with no way in; the profile is the menu's
 * first item there. Hover and focus stay out of it at that width, because a touch fires both
 * on the way to the click and would open the menu only for the tap to close it again.
 */
export const AccountMenu = () => {
  const { logout } = useAuth()
  const { open, setOpen, container, trigger } = useMenuDismiss<HTMLDivElement, HTMLAnchorElement>()
  const narrow = useNarrowScreen()

  return (
    <div
      className="account"
      ref={container}
      onMouseEnter={narrow ? undefined : () => setOpen(true)}
      onMouseLeave={narrow ? undefined : () => setOpen(false)}
      onFocus={narrow ? undefined : () => setOpen(true)}
      // Only when focus has left the menu entirely, not while it moves between the items.
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false)
      }}
    >
      <Link
        ref={trigger}
        className="icon-button"
        to="/profile"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={narrow ? 'Account' : 'Your profile'}
        title={narrow ? 'Account' : 'Your profile'}
        onClick={(event) => {
          if (narrow) {
            event.preventDefault()
            setOpen(!open)
          } else {
            setOpen(false)
          }
        }}
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
                onClick={() => {
                  setOpen(false)
                  void logout()
                }}
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
