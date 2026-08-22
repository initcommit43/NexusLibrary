import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { useAuth } from '../auth/useAuth'

const MODULES = [
  { key: 'games', label: 'Games', phase: 'Phase 1' },
  { key: 'film', label: 'Movies & TV', phase: 'Phase 6' },
  { key: 'anime', label: 'Anime & Manga', phase: 'Phase 5' },
  { key: 'books', label: 'Books', phase: 'Phase 7' },
]

export const DashboardPage = () => {
  const { user, logout } = useAuth()
  const [backend, setBackend] = useState<'checking' | 'up' | 'down'>('checking')

  useEffect(() => {
    api
      .health()
      .then(() => setBackend('up'))
      .catch(() => setBackend('down'))
  }, [])

  return (
    <div className="shell">
      <header className="shell-header">
        <div className="brand">
          <img src="/pwa-192x192.png" alt="" width={32} height={32} />
          <span>Nexus</span>
        </div>
        <div className="header-right">
          <span className="muted">{user?.username}</span>
          <button type="button" className="ghost" onClick={() => void logout()}>
            Sign out
          </button>
        </div>
      </header>

      <main className="shell-main">
        <h1>Dashboard</h1>
        <p className="muted">
          Backend:{' '}
          <span className={`status status-${backend}`}>
            {backend === 'checking' ? 'checking…' : backend === 'up' ? 'connected' : 'unreachable'}
          </span>
        </p>

        <div className="module-grid">
          {MODULES.map((module) => (
            <article key={module.key} className="card module-card">
              <h2>{module.label}</h2>
              <p className="muted">Not built yet — {module.phase}.</p>
            </article>
          ))}
        </div>
      </main>
    </div>
  )
}
