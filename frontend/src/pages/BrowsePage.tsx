import { AppShell } from '../components/AppShell'
import { useCurrentModule } from '../modules/useCurrentModule'

/**
 * Discovery — trending, seasonal, popular — as opposed to the shelves, which are yours.
 * A placeholder until the catalogue endpoints behind it exist.
 */
export const BrowsePage = () => {
  const module = useCurrentModule()

  return (
    <AppShell>
      <h1>Browse {module.label}</h1>
      <p className="muted">
        Not built yet. This is where trending and seasonal titles will live; for now, use
        search to find something by name.
      </p>
    </AppShell>
  )
}
