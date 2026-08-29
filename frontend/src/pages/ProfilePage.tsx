import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ApiError,
  api,
  type ActivityDay,
  type MediaType,
  type ProfileBanner,
  type TrackedItem,
} from '../api/client'
import { ActivityHeatmap } from '../components/ActivityHeatmap'
import { AppShell } from '../components/AppShell'
import { BannerPicker } from '../components/BannerPicker'
import { ProfileBannerFrame } from '../components/ProfileBannerFrame'
import { Figures } from '../components/Figures'
import { summarise, timeSpent } from '../components/stats'
import { FavouriteGrid } from '../components/FavouriteGrid'
import { FavouriteRows } from '../components/FavouriteRows'
import { Grip } from '../components/Grip'
import { ScopePicker } from '../components/ScopePicker'
import { useAuth } from '../auth/useAuth'
import { MODULES, mediaPathFor, type ModuleDefinition } from '../modules/registry'
import { useCurrentModule } from '../modules/useCurrentModule'

/** Half a year, which is what fits one lane of the page at a square worth hovering. */
const HISTORY_WEEKS = 26

/*
 * A module the map can say anything about. Steam knows how long a game was played and never
 * when, so a games map would be a blank half-year over a shelf someone lives in — the
 * module is left out of the picker rather than offered and then shown empty.
 */
const keepsDates = (module: ModuleDefinition) =>
  module.types.some((type) => type.mediaType !== 'GAME')

/** Arranged first, in the order they were dragged into; then everything never moved. */
const byRank = (a: TrackedItem, b: TrackedItem): number => {
  if (a.favoriteRank === null) return b.favoriteRank === null ? 0 : 1
  if (b.favoriteRank === null) return -1
  return a.favoriteRank - b.favoriteRank
}

/** Rows the reader placed, in that order; the rest keep the order the app lists them in. */
const byPlacement = (order: MediaType[]) => (a: MediaType, b: MediaType) => {
  const placed = (type: MediaType) => {
    const at = order.indexOf(type)
    return at === -1 ? Number.MAX_SAFE_INTEGER : at
  }
  return placed(a) - placed(b)
}

export const ProfilePage = () => {
  const { user } = useAuth()
  const module = useCurrentModule()
  const [entries, setEntries] = useState<TrackedItem[] | null>(null)
  const [rowOrder, setRowOrder] = useState<MediaType[]>([])
  const [banner, setBanner] = useState<ProfileBanner | null>(null)
  const [history, setHistory] = useState<ActivityDay[]>([])

  /*
   * The map opens on whatever module the header is set to, and then follows the picker
   * rather than the header: someone comparing two modules on this page is not asking to be
   * moved to one of them.
   */
  const mapped = MODULES.filter(keepsDates)
  const [scope, setScope] = useState<ModuleDefinition>(
    () => mapped.find((candidate) => candidate.slug === module.slug) ?? mapped[0],
  )
  const [picking, setPicking] = useState(false)
  const [adjusting, setAdjusting] = useState(false)
  const [arranging, setArranging] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api.listEntries(), api.favouriteRowOrder(), api.profileBanner()])
      .then(([library, arrangement, chosen]) => {
        setEntries(library)
        setRowOrder(arrangement.order)
        setBanner(chosen)
      })
      .catch((err) =>
        setError(err instanceof ApiError ? err.message : 'Could not load your library.'),
      )
  }, [])

  // Its own fetch, because it is the one thing on the page that is asked again when the
  // reader points the map somewhere else.
  useEffect(() => {
    let current = true

    api
      .activityHistory(
        HISTORY_WEEKS,
        scope.types.map((type) => type.mediaType),
      )
      // A map that will not load leaves the squares blank rather than taking the page down
      // with it: nothing else here depends on it.
      .then((days) => current && setHistory(days))
      .catch(() => current && setHistory([]))

    return () => {
      current = false
    }
  }, [scope])

  const totals = useMemo(() => summarise(entries ?? []), [entries])
  const favouriteCount = useMemo(
    () => (entries ?? []).filter((entry) => entry.favorite).length,
    [entries],
  )

  /*
   * One grid per kind of thing, in the order the app lists them, and only the kinds that
   * have any. Not per module: a module can hold two of them, and anime beside manga is a
   * shelf of two different habits — as films are beside the series you watch weekly.
   *
   * A heading over an empty grid says the kind exists; here it would only say you have
   * nothing in it, which the shelf counts below already say better.
   */
  const favourites = useMemo(() => {
    const marked = (entries ?? []).filter((entry) => entry.favorite)

    return MODULES.flatMap((module) => module.types)
      .sort((a, b) => byPlacement(rowOrder)(a.mediaType, b.mediaType))
      .map((type) => ({
        key: type.mediaType,
        label: type.label,
        marked: marked.filter((entry) => entry.mediaType === type.mediaType).sort(byRank),
      }))
      .filter((group) => group.marked.length > 0)
  }, [entries, rowOrder])

  /**
   * Writes an arrangement of the rows, the ones with nothing in them included.
   *
   * <p>An empty row is not drawn but keeps its place: it is put back at the index it was
   * stored at, so unfavouriting the last title in a row and marking another later brings the
   * row back where it was rather than at the end.
   */
  const writeRows = async (ordered: MediaType[]) => {
    const hidden = rowOrder.filter((type) => !ordered.includes(type))
    const next = [...ordered]
    for (const type of hidden) {
      next.splice(Math.min(rowOrder.indexOf(type), next.length), 0, type)
    }

    const held = rowOrder
    setRowOrder(next)
    try {
      setRowOrder((await api.replaceFavouriteRowOrder(next)).order)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that order.')
      setRowOrder(held)
    }
  }

  /*
   * Every shelf the app has, including the empty ones: a count of zero is the honest answer
   * and the link is how you go and change it.
   *
   * <p>One row rather than a tile of counts and a separate list of links. The two said less
   * between them than the row says on its own, and cost the top of the page to say it.
   */
  const shelves = useMemo(
    () =>
      MODULES.flatMap((module) =>
        module.types.map((type) => {
          const held = (entries ?? []).filter((entry) => entry.mediaType === type.mediaType)
          return {
            key: `${module.slug}/${type.slug}`,
            path: `/library/${module.slug}/${type.slug}`,
            label: type.listLabel,
            summary: summarise(held),
            time: timeSpent(held, type.mediaType),
          }
        }),
      ),
    [entries],
  )

  /**
   * Written as one arrangement of every favourite, not of the module that was dragged.
   *
   * <p>Rank is a position in that whole list, so sending only the grid that moved would
   * hand two of them the same numbers. The grid shows the new order before the request
   * answers; if the write fails the server's own copy is what comes back.
   */
  const reorder = async (key: string, ordered: TrackedItem[]) => {
    const arranged = favourites.flatMap((group) => (group.key === key ? ordered : group.marked))
    const ranks = new Map(arranged.map((entry, rank) => [entry.id, rank]))

    setEntries(
      (held) =>
        held?.map((entry) => {
          const rank = ranks.get(entry.id)
          return rank === undefined ? entry : { ...entry, favoriteRank: rank }
        }) ?? null,
    )

    try {
      const saved = await api.reorderFavourites(arranged.map((entry) => entry.id))
      const byId = new Map(saved.map((entry) => [entry.id, entry]))
      setEntries((held) => held?.map((entry) => byId.get(entry.id) ?? entry) ?? null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that order.')
      api.listEntries().then(setEntries).catch(() => {})
    }
  }

  return (
    <AppShell>
      <h1>Profile</h1>

      {/*
        * Banner and identity are one block, as on a title's page: the avatar rides the
        * banner's lower edge, so the two read as a head rather than as a picture with a
        * name under it. Contained rather than breaking out to the window — the profile is
        * about the reader, and a full-bleed image would make the picture the page.
        */}
      <section className={banner ? 'profile-head has-profile-banner' : 'profile-head'}>
        {banner && (
          <ProfileBannerFrame
            banner={banner}
            adjusting={adjusting}
            onAdjust={() => setAdjusting(true)}
            onFramed={setBanner}
            onClose={() => setAdjusting(false)}
          />
        )}

        <div className="profile-identity">
          <span className="profile-avatar" aria-hidden>
            {user?.username.charAt(0).toUpperCase()}
          </span>
          <div className="profile-name">
            <h2>{user?.username}</h2>
            <p className="muted">{user?.email}</p>
          </div>

          {/*
            * Everything about the picture in one corner, tucked under its lower edge as the
            * avatar rides over it on the other side. The credit says where the banner came
            * from rather than leaving it an anonymous backdrop; the button is out of sight
            * until the head is reached for, like the fold control on home — it belongs on
            * the thing it changes, but a profile is not read for its wallpaper.
            */}
          <div className="profile-head-aside">
            {banner && (
              <p className="profile-banner-credit muted">
                Banner from <Link to={mediaPathFor(banner)}>{banner.title}</Link>
              </p>
            )}
            <button
              type="button"
              className="banner-button"
              disabled={entries === null}
              onClick={() => {
                setAdjusting(false)
                setPicking(true)
              }}
            >
              {banner ? 'Change banner' : 'Add a banner'}
            </button>
          </div>
        </div>
      </section>

      {picking && entries !== null && (
        <BannerPicker
          entries={entries}
          chosen={banner}
          onChosen={setBanner}
          onCleared={() => setBanner(null)}
          onClose={() => setPicking(false)}
        />
      )}

      {error && (
        <p className="alert" role="alert">
          {error}
        </p>
      )}

      {entries === null && !error && <p className="muted">Loading your library…</p>}

      {entries !== null && (
        <>
          {/*
            * Two lanes: the year on the left and the totals beside it. The map is read
            * across and the figures down, so side by side each gets the shape it wants and
            * neither pushes the favourites below the fold on its own.
            */}
          <div className="profile-overview">
            <section className="status-section">
              <h2>
                Activity
                <ScopePicker modules={mapped} current={scope} onChoose={setScope} />
              </h2>
              <ActivityHeatmap days={history} weeks={HISTORY_WEEKS} />
            </section>

            <section className="status-section">
              <h2>At a glance</h2>
              <Figures
                panelled
                figures={[
                  { label: 'tracked', value: totals.tracked.toLocaleString() },
                  { label: 'completed', value: totals.completed.toLocaleString() },
                  { label: 'rated', value: totals.rated.toLocaleString() },
                  {
                    label: 'average score',
                    value: totals.meanScore === null ? '—' : totals.meanScore.toFixed(1),
                  },
                  { label: 'favourites', value: favouriteCount.toLocaleString() },
                  {
                    label: 'active days',
                    value: String(history.length),
                    hint: 'last 7 months',
                  },
                ]}
              />
            </section>
          </div>

          <section className="status-section">
            <h2>
              Favourites <span className="muted">({favouriteCount})</span>
              {favourites.length > 0 && (
                <button
                  type="button"
                  className="ghost small section-action"
                  aria-pressed={arranging}
                  onClick={() => setArranging((on) => !on)}
                >
                  {arranging ? (
                    'Done'
                  ) : (
                    <>
                      <Grip />
                      Arrange
                    </>
                  )}
                </button>
              )}
            </h2>

            {/* Said once, where it is needed: the mode is what the button already named. */}
            {arranging && (
              <p className="arrange-hint">
                Drag a cover to reorder it, or a row by its handle to move the whole row.
              </p>
            )}

            {favourites.length === 0 ? (
              <p className="muted">
                Nothing marked yet. Open an entry and use the heart in its editor to keep it
                here.
              </p>
            ) : (
              <FavouriteRows
                arranging={arranging}
                onReorder={(rows) => void writeRows(rows.map((row) => row.key as MediaType))}
                rows={favourites.map(({ key, label, marked }) => ({
                  key,
                  label,
                  content: (
                    <FavouriteGrid
                      entries={marked}
                      label={label}
                      arranging={arranging}
                      onReorder={(ordered) => void reorder(key, ordered)}
                    />
                  ),
                }))}
              />
            )}
          </section>

          <section className="status-section">
            <h2>
              Your library
              <Link className="section-action" to="/stats">
                See all stats →
              </Link>
            </h2>

            <table className="stat-table">
              <tbody>
                {shelves.map(({ key, path, label, summary, time }) => (
                  <tr key={key}>
                    <th scope="row">
                      <Link to={path}>{label}</Link>
                    </th>
                    <td>{`${summary.tracked.toLocaleString()} entries`}</td>
                    <td className="muted">
                      {summary.completed > 0 ? `${summary.completed.toLocaleString()} completed` : ''}
                    </td>
                    <td className="muted">
                      {summary.meanScore === null ? '' : `${summary.meanScore.toFixed(1)} average`}
                    </td>
                    <td className="muted">
                      {time === null ? '' : `${time.amount.toLocaleString()} ${time.unit}`}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </section>
        </>
      )}

    </AppShell>
  )
}
