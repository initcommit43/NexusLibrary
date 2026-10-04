import { Link } from 'react-router-dom'
import { useAuth } from '../auth/useAuth'
import { framedStyle, framingOf } from './framing'
import { useOwnPicture, useUploadedSrc } from './ownPicture'
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

const StatsIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <path d="M5 20V11M12 20V4M19 20v-6" strokeWidth="1.8" strokeLinecap="round" />
  </svg>
)

const SettingsIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <circle cx="12" cy="12" r="3" strokeWidth="1.8" />
    <path
      d="M19.4 15a1.7 1.7 0 0 0 .34 1.87l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.7 1.7 0 0 0-1.87-.34 1.7 1.7 0 0 0-1 1.54V21a2 2 0 1 1-4 0v-.09a1.7 1.7 0 0 0-1.1-1.55 1.7 1.7 0 0 0-1.87.34l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.7 1.7 0 0 0 .34-1.87 1.7 1.7 0 0 0-1.54-1H3a2 2 0 1 1 0-4h.09a1.7 1.7 0 0 0 1.55-1.1 1.7 1.7 0 0 0-.34-1.87l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.7 1.7 0 0 0 1.87.34H9a1.7 1.7 0 0 0 1-1.54V3a2 2 0 1 1 4 0v.09a1.7 1.7 0 0 0 1 1.54 1.7 1.7 0 0 0 1.87-.34l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.7 1.7 0 0 0-.34 1.87V9a1.7 1.7 0 0 0 1.54 1H21a2 2 0 1 1 0 4h-.09a1.7 1.7 0 0 0-1.54 1z"
      strokeWidth="1.6"
      strokeLinejoin="round"
    />
  </svg>
)

const SignOutIcon = () => (
  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" aria-hidden>
    <path
      d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
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
  const { open, setOpen, container, trigger } = useMenuDismiss<HTMLDivElement, HTMLAnchorElement>()
  // The reader's own face where it was a generic one: an uploaded picture is fetched with the
  // reader's token, a legacy character picture is a plain address.
  const picture = useOwnPicture()
  const uploaded = useUploadedSrc(picture?.version ?? null)
  const src = picture?.version ? uploaded : (picture?.imageUrl ?? null)

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
        ref={trigger}
        className="icon-button account-face"
        to="/profile"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label="Your profile"
        title="Your profile"
        onClick={() => setOpen(false)}
      >
        {src && picture ? (
          <img src={src} alt="" draggable={false} style={framedStyle(framingOf(picture))} />
        ) : (
          <UserIcon />
        )}
      </Link>

      {open && (
        <div className="account-menu">
          <ul role="menu">
            <li role="none">
              <Link role="menuitem" to="/settings" onClick={() => setOpen(false)}>
                <SettingsIcon />
                Settings
              </Link>
            </li>
            <li role="none">
              <Link role="menuitem" to="/profile" onClick={() => setOpen(false)}>
                <UserIcon />
                Profile
              </Link>
            </li>
            <li role="none">
              <Link role="menuitem" to="/stats" onClick={() => setOpen(false)}>
                <StatsIcon />
                Stats
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
                <SignOutIcon />
                Sign out
              </button>
            </li>
          </ul>
        </div>
      )}
    </div>
  )
}
