/**
 * The wordmark over every sign-in screen on a phone, where there is no header to carry it.
 * One component so the form and the notices after it cannot drift apart.
 */
export const AuthBrand = () => (
  <div className="auth-brand">
    <p className="auth-wordmark">NexusLibrary</p>
    <p className="auth-tagline">Track all your media in one spot</p>
  </div>
)
