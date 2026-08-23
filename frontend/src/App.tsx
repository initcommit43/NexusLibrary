import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ActivityPage } from './pages/ActivityPage'
import { LibraryPage } from './pages/LibraryPage'
import { EntryDetailPage } from './pages/EntryDetailPage'
import { LoginPage } from './pages/LoginPage'
import { SearchPage } from './pages/SearchPage'
import { SettingsPage } from './pages/SettingsPage'
import { SteamCallbackPage } from './pages/SteamCallbackPage'
import { RegisterPage } from './pages/RegisterPage'
import { useModules } from './modules/useModules'

/** '/' belongs to whichever module exists; the rail's switcher moves between them after. */
const LandingRedirect = () => {
  const { firstAvailable, loading } = useModules()
  if (loading) return null
  return <Navigate to={`/library/${firstAvailable.slug}`} replace />
}

export const App = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/" element={<LandingRedirect />} />
      <Route path="/library/:module" element={<LibraryPage />} />
      <Route path="/search" element={<SearchPage />} />
      <Route path="/activity" element={<ActivityPage />} />
      <Route path="/entries/:id" element={<EntryDetailPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="/settings/steam/callback" element={<SteamCallbackPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
)
