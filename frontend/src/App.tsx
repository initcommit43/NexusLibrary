import { Navigate, Route, Routes } from 'react-router-dom'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ActivityPage } from './pages/ActivityPage'
import { BrowsePage } from './pages/BrowsePage'
import { LibraryPage } from './pages/LibraryPage'
import { MediaPage } from './pages/MediaPage'
import { EntryDetailPage } from './pages/EntryDetailPage'
import { LoginPage } from './pages/LoginPage'
import { SearchPage } from './pages/SearchPage'
import { SettingsPage } from './pages/SettingsPage'
import { AniListCallbackPage } from './pages/AniListCallbackPage'
import { MalCallbackPage } from './pages/MalCallbackPage'
import { SteamCallbackPage } from './pages/SteamCallbackPage'
import { RegisterPage } from './pages/RegisterPage'
import { useCurrentModule } from './modules/useCurrentModule'
import { useModules } from './modules/useModules'

/** '/' is whichever module you were last in, so a bookmark or a reload lands where you left. */
const LandingRedirect = () => {
  const { loading } = useModules()
  const current = useCurrentModule()
  if (loading) return null
  return <Navigate to={`/library/${current.slug}`} replace />
}


export const App = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/" element={<LandingRedirect />} />
      <Route path="/library/:module" element={<LibraryPage />} />
      <Route path="/library/:module/:type" element={<LibraryPage />} />
      <Route path="/browse" element={<BrowsePage />} />
      <Route path="/search" element={<SearchPage />} />
      <Route path="/activity" element={<ActivityPage />} />
      <Route path="/entries/:id" element={<EntryDetailPage />} />
      <Route path="/media/:source/:externalId" element={<MediaPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="/settings/steam/callback" element={<SteamCallbackPage />} />
      <Route path="/settings/anilist/callback" element={<AniListCallbackPage />} />
      <Route path="/settings/mal/callback" element={<MalCallbackPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
)
