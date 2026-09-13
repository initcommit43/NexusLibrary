import type { ReactNode } from 'react'
import { AuthBrand } from './AuthBrand'
import { useNarrowScreen } from './useNarrowScreen'

type Props = {
  title: string
  children: ReactNode
  /** The one thing to do next. Wide it ends the sentence; a phone makes it the pill under it. */
  action?: ReactNode
  /** A text link out, under the pill. Only a phone draws it: wide, the sentence carries the way. */
  back?: ReactNode
}

/**
 * What an auth page shows once there is nothing left to fill in — a link sent, a password
 * changed. The same card as AuthForm deliberately: the reader is on the same page they just
 * submitted, and a differently shaped panel would read as having been sent somewhere else.
 */
export const AuthNotice = ({ title, children, action, back }: Props) => {
  const narrow = useNarrowScreen()

  if (narrow) {
    return (
      <div className="page-centered auth-page">
        <div className="card auth-form auth-notice">
          <AuthBrand />
          <h1>{title}</h1>
          <p className="auth-notice-body">{children}</p>
          {action && <div className="auth-action">{action}</div>}
          {back && <p className="auth-under">{back}</p>}
        </div>
      </div>
    )
  }

  return (
    <div className="page-centered">
      <div className="card auth-form auth-notice">
        <h1>{title}</h1>
        <p className="muted auth-aside">
          {children}
          {action && <> {action}</>}
        </p>
      </div>
    </div>
  )
}
