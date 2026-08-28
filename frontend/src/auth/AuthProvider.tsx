import { useCallback, useEffect, useMemo, useState } from 'react'
import { api, onSessionLostHandler, setAccessToken, type User } from '../api/client'
import { AuthContext, type AuthStatus } from './AuthContext'

export const AuthProvider = ({ children }: { children: React.ReactNode }) => {
  const [user, setUser] = useState<User | null>(null)
  const [status, setStatus] = useState<AuthStatus>('loading')

  // The access token is never persisted, so a reload starts anonymous. The refresh
  // cookie is what actually carries the session across reloads.
  useEffect(() => {
    let cancelled = false

    api
      .restoreSession()
      .then((session) => {
        if (cancelled) return
        setUser(session?.user ?? null)
        setStatus(session ? 'authenticated' : 'anonymous')
      })
      .catch(() => {
        if (cancelled) return
        setStatus('anonymous')
      })

    return () => {
      cancelled = true
    }
  }, [])

  useEffect(() => {
    onSessionLostHandler(() => {
      setUser(null)
      setStatus('anonymous')
    })
    return () => onSessionLostHandler(null)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    const res = await api.login({ email, password })
    setAccessToken(res.accessToken)
    setUser(res.user)
    setStatus('authenticated')
  }, [])

  const register = useCallback(async (email: string, username: string, password: string) => {
    const res = await api.register({ email, username, password })
    setAccessToken(res.accessToken)
    setUser(res.user)
    setStatus('authenticated')
  }, [])

  const logout = useCallback(async () => {
    try {
      await api.logout()
    } finally {
      setAccessToken(null)
      setUser(null)
      setStatus('anonymous')
    }
  }, [])

  // After settings renames the account, the header is still showing the old name until
  // something reads it again. This is that something.
  const refresh = useCallback(async () => {
    setUser(await api.me())
  }, [])

  const value = useMemo(
    () => ({ user, status, login, register, logout, refresh }),
    [user, status, login, register, logout, refresh],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
