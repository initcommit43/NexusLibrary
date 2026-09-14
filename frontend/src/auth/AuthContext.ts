import { createContext } from 'react'
import type { User } from '../api/client'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

/**
 * What registering led to. A deployment that requires a confirmed address hands back no
 * session, so the page has to know whether to go in or to say "check your inbox".
 */
export type RegisterOutcome = 'signed-in' | 'confirm-email'

export type AuthContextValue = {
  user: User | null
  status: AuthStatus
  login: (login: string, password: string) => Promise<void>
  register: (
    email: string,
    username: string,
    password: string,
    dateOfBirth: string,
    acceptedTerms: boolean,
    turnstileToken: string,
  ) => Promise<RegisterOutcome>
  logout: () => Promise<void>
  /** Re-reads the signed-in reader, for when a page has just changed who they are. */
  refresh: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
