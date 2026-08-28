import { createContext } from 'react'
import type { User } from '../api/client'

export type AuthStatus = 'loading' | 'authenticated' | 'anonymous'

export type AuthContextValue = {
  user: User | null
  status: AuthStatus
  login: (email: string, password: string) => Promise<void>
  register: (email: string, username: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** Re-reads the signed-in reader, for when a page has just changed who they are. */
  refresh: () => Promise<void>
}

export const AuthContext = createContext<AuthContextValue | null>(null)
