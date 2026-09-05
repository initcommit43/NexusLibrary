import type { ReactNode } from 'react'

/**
 * What an auth page shows once there is nothing left to fill in — a link sent, a password
 * changed. The same card as AuthForm deliberately: the reader is on the same page they just
 * submitted, and a differently shaped panel would read as having been sent somewhere else.
 */
export const AuthNotice = ({ title, children }: { title: string; children: ReactNode }) => (
  <div className="page-centered">
    <div className="card auth-form">
      <h1>{title}</h1>
      <p className="muted">{children}</p>
    </div>
  </div>
)
