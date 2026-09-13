import { useLocation, useNavigate } from 'react-router-dom'

const ChevronLeftIcon = () => (
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" aria-hidden>
    <path d="m15 6-6 6 6 6" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
)

/**
 * The way back a phone gets in place of the header, as the native app draws it.
 *
 * <p>Back through the reader's own history when there is some. A page opened straight from a
 * link has nothing behind it in this app, so it goes home rather than out of the site.
 */
export const BackButton = () => {
  const navigate = useNavigate()
  const { key } = useLocation()

  return (
    <button
      type="button"
      className="icon-button back-button"
      aria-label="Back"
      title="Back"
      onClick={() => (key === 'default' ? navigate('/') : navigate(-1))}
    >
      <ChevronLeftIcon />
    </button>
  )
}
