import { Navigate, Route, Routes, useParams } from 'react-router-dom'
import { ProtectedRoute } from './components/ProtectedRoute'
import { ActivityPage } from './pages/ActivityPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { BrowsePage } from './pages/BrowsePage'
import { ShelfPage } from './pages/ShelfPage'
import { LibraryPage } from './pages/LibraryPage'
import { MediaPage } from './pages/MediaPage'
import { HomePage } from './pages/HomePage'
import { ProfilePage } from './pages/ProfilePage'
import { StatsPage } from './pages/StatsPage'
import { LoginPage } from './pages/LoginPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { SearchPage } from './pages/SearchPage'
import { SettingsPage } from './pages/SettingsPage'
import { StudioPage } from './pages/StudioPage'
import { AniListCallbackPage } from './pages/AniListCallbackPage'
import { MalCallbackPage } from './pages/MalCallbackPage'
import { SimklCallbackPage } from './pages/SimklCallbackPage'
import { SteamCallbackPage } from './pages/SteamCallbackPage'
import { RegisterPage } from './pages/RegisterPage'
import { defaultTypeOf, moduleBySlug } from './modules/registry'

/** '/' is whichever module you were last in, so a bookmark or a reload lands where you left. */
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
    <Route path="/forgot-password" element={<ForgotPasswordPage />} />
    {/* Where the mailed link lands, so it stays outside ProtectedRoute: whoever opens it
        cannot sign in, which is the whole reason they were sent one. */}
    <Route path="/reset-password" element={<ResetPasswordPage />} />
    <Route element={<ProtectedRoute />}>
      <Route path="/" element={<HomePage />} />
      <Route path="/library/:module" element={<ModuleRedirect />} />
      <Route path="/library/:module/:type" element={<LibraryPage />} />
      <Route path="/browse" element={<BrowsePage />} />
      <Route path="/browse/:moduleSlug/:typeSlug/:shelfId" element={<ShelfPage />} />
      <Route path="/search" element={<SearchPage />} />
      <Route path="/activity" element={<ActivityPage />} />
      <Route path="/notifications" element={<NotificationsPage />} />
      <Route path="/profile" element={<ProfilePage />} />
      <Route path="/stats" element={<StatsPage />} />
      <Route path="/stats/:lens" element={<StatsPage />} />
      {/* The bare path and a lens without a section both resolve inside the page itself. */}
      <Route path="/media/:source/:externalId" element={<MediaPage />} />
      <Route path="/studio/:source/:studioId" element={<StudioPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="/settings/steam/callback" element={<SteamCallbackPage />} />
      <Route path="/settings/anilist/callback" element={<AniListCallbackPage />} />
      <Route path="/settings/mal/callback" element={<MalCallbackPage />} />
      <Route path="/settings/simkl/callback" element={<SimklCallbackPage />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>
)
