import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ActivityPage } from './pages/ActivityPage'
import { BrowsePage } from './pages/BrowsePage'
import { ShelfPage } from './pages/ShelfPage'
import { LibraryPage } from './pages/LibraryPage'
import { MediaPage } from './pages/MediaPage'
import { ProfilePage } from './pages/ProfilePage'
import { LoginPage } from './pages/LoginPage'
import { SearchPage } from './pages/SearchPage'
import { SettingsPage } from './pages/SettingsPage'
import { AniListCallbackPage } from './pages/AniListCallbackPage'
import { MalCallbackPage } from './pages/MalCallbackPage'
import { SimklCallbackPage } from './pages/SimklCallbackPage'
import { SteamCallbackPage } from './pages/SteamCallbackPage'
import { RegisterPage } from './pages/RegisterPage'
import { defaultTypeOf, moduleBySlug } from './modules/registry'
import { useCurrentModule } from './modules/useCurrentModule'
import { useModules } from './modules/useModules'

/** '/' is whichever module you were last in, so a bookmark or a reload lands where you left. */
const LandingRedirect = () => {
  const { loading } = useModules()
  const current = useCurrentModule()
  if (loading) return null
  return <Navigate to={`/library/${current.slug}`} replace />
}

/**
 * A module has no page of its own — it is only a set of shelves — so the bare path opens the
 * first one. Callers link here rather than to a shelf so that which shelf comes first stays
 * the registry's business.
 */
const ModuleRedirect = () => {
  const module = moduleBySlug(useParams().module)
  if (!module) return <Navigate to="/" replace />
  return <Navigate to={`/library/${module.slug}/${defaultTypeOf(module).slug}`} replace />
}

export const App = () => (
  <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/" element={<LandingRedirect />} />
      <Route path="/library/:module" element={<ModuleRedirect />} />
      <Route path="/library/:module/:type" element={<LibraryPage />} />
      <Route path="/browse" element={<BrowsePage />} />
      <Route path="/browse/:moduleSlug/:typeSlug/:shelfId" element={<ShelfPage />} />
      <Route path="/search" element={<SearchPage />} />
      <Route path="/activity" element={<ActivityPage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/media/:source/:externalId" element={<MediaPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="/settings/steam/callback" element={<SteamCallbackPage />} />
      <Route path="/settings/anilist/callback" element={<AniListCallbackPage />} />
      <Route path="/settings/mal/callback" element={<MalCallbackPage />} />
      <Route path="/settings/simkl/callback" element={<SimklCallbackPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
)
